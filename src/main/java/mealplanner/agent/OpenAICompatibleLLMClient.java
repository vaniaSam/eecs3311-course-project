package mealplanner.agent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * <b>Adapter pattern - Adapter.</b> Adapts the provider-neutral
 * {@link LLMClient} interface to the OpenAI-compatible
 * {@code POST /chat/completions} HTTP API, which is offered by OpenAI
 * (gpt-4o-mini), Groq, Google Gemini, OpenRouter and local Ollama.
 *
 * <p>Replaces the Stage 1 {@code LangChain4jLLMClient}: the same adapter role
 * is kept, but implemented with the JDK HTTP client so the project needs no
 * large framework and can use any cheap or free model.</p>
 */
public class OpenAICompatibleLLMClient implements LLMClient {

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final double temperature;
    private final AtomicLong totalTokens = new AtomicLong();

    /**
     * @param baseUrl        endpoint root such as {@code https://api.openai.com/v1}
     * @param apiKey         secret key (kept in memory only, never logged)
     * @param model          model name such as {@code gpt-4o-mini}
     * @param timeoutSeconds per-request timeout
     */
    public OpenAICompatibleLLMClient(String baseUrl, String apiKey, String model, int timeoutSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.temperature = 0.3;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public String complete(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("At least one message is required");
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", temperature);
        JsonArray array = new JsonArray();
        for (ChatMessage m : messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role());
            o.addProperty("content", m.content());
            array.add(o);
        }
        body.add("messages", array);

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        // A provider-side parsing failure (e.g. gpt-oss on Groq producing unreadable output) is random, so the
        // same request is sent once more before the error is reported.
        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (HttpTimeoutException e) {
                throw new LLMException("The LLM request timed out after " + timeout.toSeconds() + " s", e);
            } catch (IOException e) {
                throw new LLMException("Could not reach the LLM service: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LLMException("The LLM request was interrupted", e);
            }
            try {
                return parse(response.statusCode(), response.body());
            } catch (ProviderParseException e) {
                if (attempt >= MAX_PARSE_ATTEMPTS) {
                    throw new LLMException("The model's reply could not be read by the LLM provider after "
                        + MAX_PARSE_ATTEMPTS + " attempts. Please try again.", e, true);
                }
            }
        }
    }

    /** Requests sent in total when the provider reports that it could not parse the model's output. */
    static final int MAX_PARSE_ATTEMPTS = 2;

    /** The provider could not parse the model's own output; sending the request again usually succeeds. */
    static final class ProviderParseException extends LLMException {
        private static final long serialVersionUID = 1L;

        ProviderParseException(String message) {
            super(message, null, true);
        }
    }

    /** Extracts the reply text from a chat-completions response body. Package-private for tests. */
    String parse(int status, String responseBody) {
        if (status == 401 || status == 403) {
            throw new LLMException("The LLM provider rejected the API key (HTTP " + status + "). Check LLM_API_KEY.");
        }
        if (status == 429) {
            throw new LLMException("The LLM provider rate limit or quota was exceeded (HTTP 429). Try again later.");
        }
        if (status == 400) {
            String attempted = failedToolGeneration(responseBody);
            if (attempted != null) {
                // Some models (e.g. gpt-oss on Groq) answer a tool-using prompt with a native function
                // call, which the provider rejects because no API tools were declared. The attempted call
                // is returned in "failed_generation"; hand it to the agent, which accepts that shape.
                return attempted;
            }
            if (isOutputParseFailure(responseBody)) {
                throw new ProviderParseException("The LLM provider could not parse the model's output");
            }
        }
        if (status < 200 || status >= 300) {
            throw new LLMException("The LLM provider returned HTTP " + status + ": " + abbreviate(responseBody));
        }
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonElement usage = json.get("usage");
            if (usage != null && usage.isJsonObject() && usage.getAsJsonObject().has("total_tokens")) {
                totalTokens.addAndGet(usage.getAsJsonObject().get("total_tokens").getAsLong());
            }
            JsonObject message = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message");
            JsonElement content = message.get("content");
            if (content == null || content.isJsonNull() || content.getAsString().isBlank()) {
                throw new LLMException("The LLM returned an empty reply");
            }
            return content.getAsString();
        } catch (RuntimeException e) {
            if (e instanceof LLMException) {
                throw e;
            }
            throw new LLMException("Unexpected response format from the LLM provider", e);
        }
    }

    /** True for an HTTP 400 body saying the provider could not parse the model's generated output. */
    static boolean isOutputParseFailure(String body) {
        try {
            JsonObject error = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");
            if (error == null) {
                return false;
            }
            String code = JsonExtractor.string(error, "code", "");
            String message = JsonExtractor.string(error, "message", "");
            return code.equals("output_parse_failed") || message.startsWith("Parsing failed");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Returns error.failed_generation for a "tool_use_failed" error body, otherwise {@code null}. */
    static String failedToolGeneration(String body) {
        try {
            JsonObject error = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");
            if (error != null && "tool_use_failed".equals(JsonExtractor.string(error, "code", ""))) {
                String generation = JsonExtractor.string(error, "failed_generation", "");
                return generation.isBlank() ? null : generation;
            }
        } catch (RuntimeException e) {
            // not a JSON error body
        }
        return null;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String describe() {
        String host = baseUrl.replaceFirst("^https?://", "");
        int slash = host.indexOf('/');
        return model + " via " + (slash > 0 ? host.substring(0, slash) : host);
    }

    @Override
    public long getTotalTokens() {
        return totalTokens.get();
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replaceAll("\\s+", " ");
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "..." : oneLine;
    }
}
