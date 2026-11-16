package mealplanner.service;

import java.util.ArrayList;
import java.util.List;

import mealplanner.model.IngredientRequirement;
import mealplanner.model.Pantry;
import mealplanner.model.Recipe;

/**
 * Compares recipe ingredients against usable (non-expired) pantry stock.
 * Shared by {@link RecipeService} (F03) and the ranking strategies (F04/F06).
 */
public class RecipeMatcher {

    /** Ingredients for which the pantry holds no usable stock in any unit. */
    public List<String> getMissingIngredients(Recipe recipe, Pantry pantry) {
        requireArgs(recipe, pantry);
        List<String> missing = new ArrayList<>();
        for (String ingredient : recipe.getIngredients()) {
            if (!pantry.hasUsable(ingredient)) {
                missing.add(ingredient);
            }
        }
        return missing;
    }

    /** Ingredients for which some usable stock exists (quantity may be insufficient). */
    public List<String> getMatchedIngredients(Recipe recipe, Pantry pantry) {
        requireArgs(recipe, pantry);
        List<String> matched = new ArrayList<>(recipe.getIngredients());
        matched.removeAll(getMissingIngredients(recipe, pantry));
        return matched;
    }

    /** Percentage (0-100) of recipe ingredients present in the pantry. */
    public double getMatchPercentage(Recipe recipe, Pantry pantry) {
        requireArgs(recipe, pantry);
        int total = recipe.getIngredients().size();
        int missing = getMissingIngredients(recipe, pantry).size();
        return 100.0 * (total - missing) / total;
    }

    /**
     * True if every requirement is covered in the same unit and in sufficient
     * quantity, i.e. the recipe can be cooked completely from the pantry.
     */
    public boolean isFullyCookable(Recipe recipe, Pantry pantry) {
        requireArgs(recipe, pantry);
        for (IngredientRequirement r : recipe.getIngredientRequirements()) {
            if (pantry.getAvailableQuantity(r.getName(), r.getUnit()) + 1e-9 < r.getQuantity()) {
                return false;
            }
        }
        return true;
    }

    private static void requireArgs(Recipe recipe, Pantry pantry) {
        if (recipe == null || pantry == null) {
            throw new IllegalArgumentException("Recipe and pantry cannot be null");
        }
    }
}
