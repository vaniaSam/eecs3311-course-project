package mealplanner.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import mealplanner.config.AppConfig;

/** Configuration, JSON extraction and the HTTP adapter (against a local OpenAI-protocol stub server). */
class LLMInfrastructureTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(int status, String body, AtomicReference<String> requestBody,
                               AtomicReference<String> authHeader) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @Test
    void adapterSendsChatCompletionRequestAndParsesReply() throws IOException {
        AtomicReference<String> sent = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        String url = startServer(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hello\"}}],"
            + "\"usage\":{\"total_tokens\":42}}", sent, auth);
        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(url, "test-key", "tiny-model", 10);

        assertEquals("hello", client.complete(List.of(ChatMessage.system("sys"), ChatMessage.user("hi"))));
        JsonObject body = JsonParser.parseString(sent.get()).getAsJsonObject();
        assertEquals("tiny-model", body.get("model").getAsString());
        assertEquals(2, body.getAsJsonArray("messages").size());
        assertEquals("user", body.getAsJsonArray("messages").get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("Bearer test-key", auth.get());
        assertEquals(42, client.getTotalTokens());
        assertTrue(client.describe().startsWith("tiny-model via 127.0.0.1"));
        assertFalse(client.describe().contains("test-key"));
    }

    @Test
    void adapterReportsHttpErrorsAsLLMException() throws IOException {
        String url = startServer(401, "{\"error\":\"bad key\"}", new AtomicReference<>(), new AtomicReference<>());
        LLMException e = assertThrows(LLMException.class,
            () -> new OpenAICompatibleLLMClient(url, "k", "m", 10).complete("hi"));
        assertTrue(e.getMessage().contains("rejected the API key"));
    }

    @Test
    void adapterHandlesMalformedAndEmptyBodies() {
        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient("http://x/v1", "k", "m", 10);
        assertThrows(LLMException.class, () -> client.parse(200, "not json"));
        assertThrows(LLMException.class, () -> client.parse(200, "{\"choices\":[{\"message\":{\"content\":\"\"}}]}"));
        assertThrows(LLMException.class, () -> client.parse(429, "{}"));
        assertThrows(LLMException.class, () -> client.parse(500, "server error"));
    }

    @Test
    void unreachableServerIsReported() {
        LLMException e = assertThrows(LLMException.class,
            () -> new OpenAICompatibleLLMClient("http://127.0.0.1:1/v1", "k", "m", 5).complete("hi"));
        assertTrue(e.getMessage().contains("Could not reach"));
    }

    @Test
    void configReadsDotenvAndEnvironmentOverrides(@TempDir Path dir) throws IOException {
        Path env = dir.resolve(".env");
        Files.writeString(env, "# comment\nLLM_API_KEY=\"abc123\"\nexport LLM_MODEL=llama3\nLLM_BASE_URL=http://h/v1/\n");
        AppConfig fromFile = AppConfig.load(env, Map.of());
        assertTrue(fromFile.isLlmConfigured());
        assertEquals("abc123", fromFile.getApiKey());
        assertEquals("llama3", fromFile.getModel());
        assertEquals("http://h/v1", fromFile.getBaseUrl());
        assertFalse(fromFile.toString().contains("abc123"), "toString never prints the key");

        AppConfig overridden = AppConfig.load(env, Map.of("LLM_MODEL", "gpt-4o-mini"));
        assertEquals("gpt-4o-mini", overridden.getModel());
    }

    @Test
    void placeholderOrMissingKeyMeansNotConfigured(@TempDir Path dir) throws IOException {
        Path env = dir.resolve(".env");
        Files.writeString(env, "LLM_API_KEY=YOUR_LOCAL_API_KEY_HERE\n");
        assertFalse(AppConfig.load(env, Map.of()).isLlmConfigured());
        AppConfig none = AppConfig.load(dir.resolve("missing.env"), Map.of());
        assertFalse(none.isLlmConfigured());
        assertEquals(AppConfig.DEFAULT_MODEL, none.getModel());
        assertInstanceOf(UnconfiguredLLMClient.class, LLMClientFactory.create(none));
        assertThrows(LLMException.class, () -> LLMClientFactory.create(none).complete("x"));
        assertInstanceOf(OpenAICompatibleLLMClient.class,
            LLMClientFactory.create(new AppConfig("real", null, null, 5)));
    }

    @Test
    void jsonExtractorToleratesFencesAndProse() {
        assertEquals(1, JsonExtractor.extractObject("```json\n{\"a\":1}\n```").get("a").getAsInt());
        assertEquals("x", JsonExtractor.extractObject("Sure! Here it is: {\"b\":\"x\"} Enjoy.").get("b")
            .getAsString());
        assertThrows(LLMException.class, () -> JsonExtractor.extractObject("no json here"));
        assertThrows(LLMException.class, () -> JsonExtractor.extractObject("{broken: }"));
        assertThrows(LLMException.class, () -> JsonExtractor.extractObject(""));
    }

    @Test
    void groqToolUseFailedReturnsTheAttemptedCall() {
        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient("http://x/v1", "k", "m", 10);
        String body = "{\"error\":{\"message\":\"Tool choice is none, but model called a tool\","
            + "\"type\":\"invalid_request_error\",\"code\":\"tool_use_failed\","
            + "\"failed_generation\":\"{\\\"name\\\": \\\"expiring_items\\\", \\\"arguments\\\": {\\\"days\\\": 3}}\"}}";
        String reply = client.parse(400, body);
        assertEquals("expiring_items", JsonExtractor.extractObject(reply).get("name").getAsString());
        assertThrows(LLMException.class, () -> client.parse(400, "{\"error\":{\"code\":\"other\"}}"));
    }

    private static final String PARSE_FAILED = "{\"error\":{\"message\":\"Parsing failed. The model generated output "
        + "that could not be parsed. Please adjust your prompt. See 'failed_generation' for more details.\","
        + "\"type\":\"invalid_request_error\",\"code\":\"output_parse_failed\",\"failed_generation\":\"<|channel|>\"}}";

    private String startSequence(java.util.concurrent.atomic.AtomicInteger calls, int failuresBeforeSuccess)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            boolean fail = calls.incrementAndGet() <= failuresBeforeSuccess;
            byte[] bytes = (fail ? PARSE_FAILED
                : "{\"choices\":[{\"message\":{\"content\":\"{\\\"answer\\\":\\\"ok\\\"}\"}}]}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(fail ? 400 : 200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @Test
    void providerParseFailureIsRetriedOnce() throws IOException {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        String url = startSequence(calls, 1);
        String reply = new OpenAICompatibleLLMClient(url, "k", "m", 10).complete("hi");
        assertEquals("ok", JsonExtractor.extractObject(reply).get("answer").getAsString());
        assertEquals(2, calls.get());
    }

    @Test
    void repeatedProviderParseFailureIsReportedClearly() throws IOException {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        String url = startSequence(calls, 5);
        LLMException e = assertThrows(LLMException.class,
            () -> new OpenAICompatibleLLMClient(url, "k", "m", 10).complete("hi"));
        assertTrue(e.getMessage().contains("could not be read"), e.getMessage());
        assertEquals(OpenAICompatibleLLMClient.MAX_PARSE_ATTEMPTS, calls.get());
        assertTrue(OpenAICompatibleLLMClient.isOutputParseFailure(PARSE_FAILED));
        assertFalse(OpenAICompatibleLLMClient.isOutputParseFailure("{\"error\":{\"code\":\"tool_use_failed\"}}"));
    }

    private String startRateLimited(java.util.concurrent.atomic.AtomicInteger calls, int limitedCalls, String retryAfter)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            boolean limited = calls.incrementAndGet() <= limitedCalls;
            byte[] bytes = (limited ? "{\"error\":{\"message\":\"Rate limit reached\",\"code\":\"rate_limit_exceeded\"}}"
                : "{\"choices\":[{\"message\":{\"content\":\"fine\"}}]}").getBytes(StandardCharsets.UTF_8);
            if (limited) {
                exchange.getResponseHeaders().add("retry-after", retryAfter);
            }
            exchange.sendResponseHeaders(limited ? 429 : 200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @Test
    void rateLimitWaitsForRetryAfterThenSucceeds() throws IOException {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<Long> waits = new java.util.ArrayList<>();
        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(startRateLimited(calls, 2, "2.5"), "k", "m", 10);
        client.setSleeper(waits::add);
        assertEquals("fine", client.complete("hi"));
        assertEquals(3, calls.get());
        assertEquals(java.util.List.of(2500L, 2500L), waits);
    }

    @Test
    void rateLimitIsReportedWhenTheWaitIsTooLongOrKeepsHappening() throws IOException {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(startRateLimited(calls, 9, "120"), "k", "m", 10);
        client.setSleeper(ms -> fail("should not wait 120 s"));
        LLMException e = assertThrows(LLMException.class, () -> client.complete("hi"));
        assertTrue(e.getMessage().contains("429"));
        assertEquals(1, calls.get());
        assertEquals(OpenAICompatibleLLMClient.DEFAULT_RATE_LIMIT_WAIT_MILLIS, OpenAICompatibleLLMClient.retryAfterMillis("soon"));
    }
}
