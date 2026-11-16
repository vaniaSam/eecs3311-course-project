package mealplanner.agent;

/**
 * Raised when the LLM cannot be reached, is not configured, times out, or
 * returns content the agent cannot use. Callers report the problem and leave
 * accepted application state unchanged.
 */
public class LLMException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
