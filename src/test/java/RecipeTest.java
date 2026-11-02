
import java.util.List;

public class RecipeTest {

    public static void main(String[] args) {

        Recipe recipe = new Recipe(
            "Chicken Rice",
            List.of("Chicken", "Rice", "Onion"),
            List.of(
                "Cook the rice",
                "Cook the chicken",
                "Combine and serve"
            ),
            30,
            2
        );

        // Verify recipe information
        if (!recipe.getName().equals("Chicken Rice")) {
            throw new AssertionError("Recipe name incorrect");
        }

        if (recipe.getCookingTimeMinutes() != 30) {
            throw new AssertionError("Cooking time incorrect");
        }

        if (recipe.getServings() != 2) {
            throw new AssertionError("Servings incorrect");
        }

        // Verify ingredient matching
        if (!recipe.containsIngredient("chicken")) {
            throw new AssertionError("Case-insensitive search failed");
        }

        if (recipe.containsIngredient("Beef")) {
            throw new AssertionError("Incorrect ingredient match");
        }

        // Verify defensive copying
        recipe.getIngredients().clear();

        if (recipe.getIngredients().size() != 3) {
            throw new AssertionError("Ingredients were modified");
        }

        System.out.println("Recipe test passed!");
    }
}
