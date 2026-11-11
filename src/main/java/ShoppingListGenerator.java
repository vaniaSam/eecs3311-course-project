
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates a shopping list from ingredients missing in the pantry.
 */
public class ShoppingListGenerator {

    private final RecipeMatcher matcher = new RecipeMatcher();

    public List<String> generate(Recipe recipe, Pantry pantry) {
        if (recipe == null || pantry == null) {
            throw new IllegalArgumentException(
                "Recipe and pantry cannot be null"
            );
        }

        return new ArrayList<>(
            matcher.getMissingIngredients(recipe, pantry)
        );
    }

    /**
     * Combines missing ingredients across multiple recipes,
     * avoiding duplicate names regardless of capitalization.
     */
    public List<String> generateForRecipes(
            List<Recipe> recipes, Pantry pantry) {

        if (recipes == null || pantry == null) {
            throw new IllegalArgumentException(
                "Recipes and pantry cannot be null"
            );
        }

        Set<String> seen = new LinkedHashSet<>();
        List<String> shoppingList = new ArrayList<>();

        for (Recipe recipe : recipes) {
            for (String ingredient :
                    matcher.getMissingIngredients(recipe, pantry)) {

                String normalized =
                    ingredient.trim().toLowerCase(java.util.Locale.ROOT);

                if (seen.add(normalized)) {
                    shoppingList.add(ingredient);
                }
            }
        }

        return shoppingList;
    }
}
