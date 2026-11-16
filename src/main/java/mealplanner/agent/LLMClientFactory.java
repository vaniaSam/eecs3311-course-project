package mealplanner.agent;

import mealplanner.config.AppConfig;

/**
 * <b>Factory (Simple Factory) pattern.</b> Chooses the concrete
 * {@link LLMClient} from configuration so that no other class needs to know
 * which provider adapter, if any, is in use.
 */
public final class LLMClientFactory {

    private LLMClientFactory() {
    }

    /** Returns a working adapter when a key is configured, otherwise an {@link UnconfiguredLLMClient}. */
    public static LLMClient create(AppConfig config) {
        if (config == null || !config.isLlmConfigured()) {
            return new UnconfiguredLLMClient();
        }
        return new OpenAICompatibleLLMClient(config.getBaseUrl(), config.getApiKey(), config.getModel(),
            config.getTimeoutSeconds());
    }
}
