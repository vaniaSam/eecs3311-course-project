package mealplanner.agent;

import java.util.List;

import mealplanner.model.Recipe;

/**
 * Result of F09 leftover-based meal planning.
 *
 * @param recipe        proposed recipe, or {@code null} if none could be found
 * @param leftoversUsed leftover names the recipe uses
 * @param reason        explanation of the choice or of the failure
 * @param aiUsed        true if the LLM chose or generated the recipe
 * @param generated     true if the recipe was newly generated (not from the catalogue)
 * @param trace         agent steps
 */
public record LeftoverPlanResult(Recipe recipe, List<String> leftoversUsed, String reason, boolean aiUsed,
                                 boolean generated, AgentTrace trace) {
}
