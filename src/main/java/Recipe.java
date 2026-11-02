
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a recipe in the meal-planning application.
 *
 * Stores recipe information used for searching,
 * filtering, and generating meal plans.
 */
public class Recipe {

    private final String name;
    private final List<String> ingredients;
    private final List<String> instructions;
    private final int cookingTimeMinutes;
    private final int servings;

    /**
     * Creates a recipe with its ingredients and instructions.
     */
    public Recipe(
            String name,
            List<String> ingredients,
            List<String> instructions,
            int cookingTimeMinutes,
            int servings) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                "Recipe name cannot be empty"
            );
        }

        Objects.requireNonNull(
            ingredients, "Ingredients cannot be null"
        );

        Objects.requireNonNull(
            instructions, "Instructions cannot be null"
        );

        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException(
                "Recipe must have at least one ingredient"
            );
        }

        if (cookingTimeMinutes <= 0 || servings <= 0) {
            throw new IllegalArgumentException(
                "Cooking time and servings must be positive"
            );
        }

        this.name = name.trim();
        this.ingredients = new ArrayList<>(ingredients);
        this.instructions = new ArrayList<>(instructions);
        this.cookingTimeMinutes = cookingTimeMinutes;
        this.servings = servings;
    }

    public String getName() {
        return name;
    }

    public List<String> getIngredients() {
        return new ArrayList<>(ingredients);
    }

    public List<String> getInstructions() {
        return new ArrayList<>(instructions);
    }

    public int getCookingTimeMinutes() {
        return cookingTimeMinutes;
    }

    public int getServings() {
        return servings;
    }

    /**
     * Checks whether a recipe contains an ingredient.
     * Ingredient names are compared without case sensitivity.
     */
    public boolean containsIngredient(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return false;
        }

        for (String ingredient : ingredients) {
            if (ingredient.equalsIgnoreCase(
                    ingredientName.trim())) {
                return true;
            }
        }

        return false;
    }
}
