package mealplanner.agent;

import mealplanner.model.Recipe;
import mealplanner.model.ValidationResult;

/**
 * Result of F05 AI recipe generation (also used by F09 when a new recipe is generated from leftovers).
 *
 * @param recipe     the accepted recipe, or {@code null} on failure
 * @param validation checks applied to the final proposal
 * @param message    user-facing explanation of success or failure
 * @param trace      agent steps
 */
public record RecipeGenerationResult(Recipe recipe, ValidationResult validation, String message, AgentTrace trace) {

    public boolean success() {
        return recipe != null;
    }
}
