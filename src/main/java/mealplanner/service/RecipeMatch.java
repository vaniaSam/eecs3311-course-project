package mealplanner.service;

import java.util.List;

import mealplanner.model.Recipe;

/**
 * Result row of an ingredient-based search (F03): which requested ingredients
 * a recipe uses, which recipe ingredients are still missing, and whether the
 * recipe can be cooked completely from the pantry.
 */
public record RecipeMatch(Recipe recipe, List<String> matchedIngredients,
                          List<String> missingIngredients, boolean fullyCookable) {

    public RecipeMatch {
        matchedIngredients = List.copyOf(matchedIngredients);
        missingIngredients = List.copyOf(missingIngredients);
    }

    /** One-line summary for list displays. */
    public String summary() {
        String status = fullyCookable ? "COOKABLE NOW"
            : missingIngredients.isEmpty() ? "all ingredients present (check quantities)"
            : "missing: " + String.join(", ", missingIngredients);
        return recipe.getName() + " [" + recipe.getId() + "] - uses " + String.join(", ", matchedIngredients)
            + " | " + status;
    }
}
