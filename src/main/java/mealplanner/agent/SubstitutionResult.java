package mealplanner.agent;

import java.util.List;

/**
 * Result of F08 ingredient substitution.
 *
 * @param ingredient    the ingredient being replaced
 * @param substitutions proposals, acceptable ones first
 * @param aiUsed        true if the LLM contributed proposals
 * @param message       caveats (never claims guaranteed allergen safety)
 * @param trace         agent steps
 */
public record SubstitutionResult(String ingredient, List<Substitution> substitutions, boolean aiUsed, String message,
                                 AgentTrace trace) {
}
