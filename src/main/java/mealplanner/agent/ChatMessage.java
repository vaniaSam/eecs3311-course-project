package mealplanner.agent;

import java.util.Objects;

/**
 * One message of an LLM conversation.
 *
 * @param role    "system", "user" or "assistant"
 * @param content message text
 */
public record ChatMessage(String role, String content) {

    public ChatMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
