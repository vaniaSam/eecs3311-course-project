package mealplanner.agent;

/**
 * Raised when the LLM cannot be reached, is not configured, times out, or
 * returns content the agent cannot use. Callers report the problem and leave
 * accepted application state unchanged.
 */
public class LLMException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final boolean providerParseFailure;

    public LLMException(String message) {
        this(message, null, false);
    }

    public LLMException(String message, Throwable cause) {
        this(message, cause, false);
    }

    /**
     * @param providerParseFailure true if the provider could not read the model's own output; a follow-up
     *                             message asking the model to answer again usually succeeds
     */
    public LLMException(String message, Throwable cause, boolean providerParseFailure) {
        super(message, cause);
        this.providerParseFailure = providerParseFailure;
    }

    /** True if the provider could not parse the model's output (a recoverable model glitch). */
    public boolean isProviderParseFailure() {
        return providerParseFailure;
    }
}
