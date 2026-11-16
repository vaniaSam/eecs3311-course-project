package mealplanner.agent;

import java.util.List;

/**
 * <b>Adapter pattern - Target interface.</b> The agent depends only on this
 * provider-neutral interface; concrete adapters translate it to a specific
 * vendor API. Tests substitute scripted implementations.
 */
public interface LLMClient {

    /**
     * Sends a conversation and returns the assistant's reply text.
     *
     * @throws LLMException if the model is unavailable or the call fails
     */
    String complete(List<ChatMessage> messages);

    /** Stage 1 signature: single user prompt. */
    default String complete(String prompt) {
        return complete(List.of(ChatMessage.user(prompt)));
    }

    /** True if this client can actually reach a model (a key is configured). */
    boolean isAvailable();

    /** Short human-readable description, e.g. "gpt-4o-mini via api.openai.com". Never contains secrets. */
    String describe();

    /** Total tokens reported by the provider for this session (0 if unknown). */
    default long getTotalTokens() {
        return 0;
    }
}
