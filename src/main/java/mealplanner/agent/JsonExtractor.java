package mealplanner.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Extracts a JSON object from free-form LLM text. Models often wrap JSON in
 * Markdown fences or add a sentence before it; this class tolerates that but
 * rejects anything that is not a single JSON object.
 */
public final class JsonExtractor {

    private JsonExtractor() {
    }

    /**
     * @throws LLMException if no valid JSON object can be found
     */
    public static JsonObject extractObject(String text) {
        if (text == null || text.isBlank()) {
            throw new LLMException("The AI reply was empty");
        }
        String t = text.trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LLMException("The AI reply did not contain a JSON object");
        }
        try {
            JsonElement e = JsonParser.parseString(t.substring(start, end + 1));
            if (!e.isJsonObject()) {
                throw new LLMException("The AI reply was not a JSON object");
            }
            return e.getAsJsonObject();
        } catch (RuntimeException ex) {
            if (ex instanceof LLMException) {
                throw ex;
            }
            throw new LLMException("The AI reply contained malformed JSON", ex);
        }
    }

    /** Reads a string member or returns {@code fallback} when missing, null or not a primitive. */
    public static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            return fallback;
        }
        return e.getAsString();
    }
}
