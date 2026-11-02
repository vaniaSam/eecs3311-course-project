

import java.util.ArrayList;
import java.util.List;

/**
 * Stores recipes and provides search and filtering functionality.
 */
public class RecipeSearch {

    private final List<Recipe> recipes = new ArrayList<>();

    public void addRecipe(Recipe recipe) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe cannot be null");
        }
        recipes.add(recipe);
    }

    public List<Recipe> getAllRecipes() {
        return new ArrayList<>(recipes);
    }

    /**
     * Searches for recipes containing a keyword in their name.
     */
    public List<Recipe> searchByName(String keyword) {
        List<Recipe> results = new ArrayList<>();

        if (keyword == null || keyword.isBlank()) {
            return results;
        }

        for (Recipe recipe : recipes) {
            if (recipe.getName().toLowerCase()
                    .contains(keyword.trim().toLowerCase())) {
                results.add(recipe);
            }
        }

        return results;
    }

    /**
     * Finds recipes containing the specified ingredient.
     */
    public List<Recipe> searchByIngredient(String ingredient) {
        List<Recipe> results = new ArrayList<>();

        for (Recipe recipe : recipes) {
            if (recipe.containsIngredient(ingredient)) {
                results.add(recipe);
            }
        }

        return results;
    }

    /**
     * Finds recipes that can be prepared within the given time.
     */
    public List<Recipe> filterByCookingTime(int maxMinutes) {
        List<Recipe> results = new ArrayList<>();

        if (maxMinutes <= 0) {
            return results;
        }

        for (Recipe recipe : recipes) {
            if (recipe.getCookingTimeMinutes() <= maxMinutes) {
                results.add(recipe);
            }
        }

        return results;
    }

    public int size() {
        return recipes.size();
    }
}
