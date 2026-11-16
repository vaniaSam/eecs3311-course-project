package mealplanner.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Application configuration for the LLM connection.
 *
 * <p>Values are read from a local, git-ignored {@code .env} file in the
 * working directory and may be overridden by real environment variables of
 * the same name. Real API keys are therefore never part of the source code
 * or the repository.</p>
 *
 * <ul>
 *   <li>{@code LLM_API_KEY} - provider key (required for AI features)</li>
 *   <li>{@code LLM_BASE_URL} - OpenAI-compatible endpoint, default {@value #DEFAULT_BASE_URL}</li>
 *   <li>{@code LLM_MODEL} - model name, default {@value #DEFAULT_MODEL}</li>
 *   <li>{@code LLM_TIMEOUT_SECONDS} - request timeout, default 60</li>
 * </ul>
 */
public final class AppConfig {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_MODEL = "gpt-4o-mini";
    /** Placeholder used in .env.example; treated as "no key". */
    public static final String PLACEHOLDER_KEY = "YOUR_LOCAL_API_KEY_HERE";

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;

    public AppConfig(String apiKey, String baseUrl, String model, int timeoutSeconds) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = blank(baseUrl) ? DEFAULT_BASE_URL : stripSlash(baseUrl.trim());
        this.model = blank(model) ? DEFAULT_MODEL : model.trim();
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60;
    }

    /** Loads {@code .env} from the working directory (if present) and applies environment overrides. */
    public static AppConfig load() {
        String override = System.getProperty("mealplanner.env");
        return load(Path.of(override != null ? override : ".env"), System.getenv());
    }

    /**
     * Loads configuration from a dotenv file and an environment map (environment wins).
     * A missing file is not an error: AI features then report that no LLM is configured.
     */
    public static AppConfig load(Path dotenv, Map<String, String> environment) {
        Map<String, String> values = new HashMap<>();
        if (dotenv != null && Files.isRegularFile(dotenv)) {
            try {
                for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
                    parseLine(line, values);
                }
            } catch (IOException e) {
                System.err.println("Warning: could not read " + dotenv + ": " + e.getMessage());
            }
        }
        for (String key : new String[] {"LLM_API_KEY", "LLM_BASE_URL", "LLM_MODEL", "LLM_TIMEOUT_SECONDS"}) {
            String v = environment.get(key);
            if (!blank(v)) {
                values.put(key, v);
            }
        }
        int timeout = 60;
        try {
            timeout = Integer.parseInt(values.getOrDefault("LLM_TIMEOUT_SECONDS", "60").trim());
        } catch (NumberFormatException ignored) {
            // keep default
        }
        return new AppConfig(values.get("LLM_API_KEY"), values.get("LLM_BASE_URL"), values.get("LLM_MODEL"), timeout);
    }

    /** Parses one {@code KEY=VALUE} line; comments and blank lines are ignored, quotes are stripped. */
    static void parseLine(String line, Map<String, String> into) {
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) {
            return;
        }
        if (t.startsWith("export ")) {
            t = t.substring(7).trim();
        }
        int eq = t.indexOf('=');
        String key = t.substring(0, eq).trim();
        String value = t.substring(eq + 1).trim();
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        into.put(key, value);
    }

    /** True when a real (non-placeholder) API key is present. */
    public boolean isLlmConfigured() {
        return !apiKey.isEmpty() && !apiKey.equals(PLACEHOLDER_KEY);
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /** Safe description that never includes the key. */
    @Override
    public String toString() {
        return "AppConfig[model=" + model + ", baseUrl=" + baseUrl + ", key=" + (isLlmConfigured() ? "set" : "missing")
            + "]";
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
