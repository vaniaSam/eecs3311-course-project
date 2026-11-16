package mealplanner.agent;

import java.util.List;

/**
 * Stand-in used when no API key is configured. Every call fails with a clear
 * {@link LLMException}, so AI-only features explain how to configure a key and
 * hybrid features fall back to their deterministic path. It never fabricates
 * model output.
 */
public class UnconfiguredLLMClient implements LLMClient {

    @Override
    public String complete(List<ChatMessage> messages) {
        throw new LLMException("No LLM is configured. Copy .env.example to .env and set LLM_API_KEY "
            + "(see README).");
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String describe() {
        return "not configured (set LLM_API_KEY in .env)";
    }
}
