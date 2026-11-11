
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Recipe {
    private final String name;
    private final List<String> ingredients;
    private final List<IngredientRequirement> requirements;
    private final List<String> instructions;
    private final int cookingTimeMinutes;
    private final int servings;

    // Original constructor retained for existing tests.
    public Recipe(String name, List<String> ingredients,
                  List<String> instructions,
                  int cookingTimeMinutes, int servings) {
        this(name, ingredients, List.of(), instructions,
             cookingTimeMinutes, servings);
    }

    private Recipe(String name, List<String> ingredients,
                   List<IngredientRequirement> requirements,
                   List<String> instructions,
                   int cookingTimeMinutes, int servings) {

        if (name == null || name.isBlank()
                || cookingTimeMinutes <= 0 || servings <= 0) {
            throw new IllegalArgumentException("Invalid recipe details");
        }

        Objects.requireNonNull(ingredients);
        Objects.requireNonNull(requirements);
        Objects.requireNonNull(instructions);

        if (ingredients.isEmpty()
                || ingredients.stream().anyMatch(
                    s -> s == null || s.isBlank())
                || instructions.stream().anyMatch(Objects::isNull)
                || requirements.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Invalid recipe content");
        }

        this.name = name.trim();
        this.ingredients = new ArrayList<>(ingredients);
        this.requirements = new ArrayList<>(requirements);
        this.instructions = new ArrayList<>(instructions);
        this.cookingTimeMinutes = cookingTimeMinutes;
        this.servings = servings;
    }

    // New factory for recipes with measured ingredients.
    public static Recipe withRequirements(
            String name,
            List<IngredientRequirement> requirements,
            List<String> instructions,
            int cookingTimeMinutes,
            int servings) {

        Objects.requireNonNull(requirements);

        if (requirements.isEmpty()) {
            throw new IllegalArgumentException(
                "Recipe must have ingredients");
        }

        List<String> names = new ArrayList<>();
        for (IngredientRequirement item : requirements) {
            names.add(Objects.requireNonNull(item).getName());
        }

        return new Recipe(name, names, requirements,
                          instructions, cookingTimeMinutes, servings);
    }

    public String getName() {
        return name;
    }

    public List<String> getIngredients() {
        return new ArrayList<>(ingredients);
    }

    public List<IngredientRequirement> getIngredientRequirements() {
        return new ArrayList<>(requirements);
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

    public boolean containsIngredient(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return false;
        }

        for (String ingredient : ingredients) {
            if (ingredient.equalsIgnoreCase(ingredientName.trim())) {
                return true;
            }
        }
        return false;
    }
}
