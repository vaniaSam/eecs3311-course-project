package mealplanner.agent;

import mealplanner.model.Recipe;

/**
 * One ranked recommendation (F04).
 *
 * @param recipe the recommended recipe
 * @param reason why it was recommended (from the LLM or the strategy)
 * @param score  the strategy score used for the deterministic ranking
 */
public record Recommendation(Recipe recipe, String reason, double score) {
}
