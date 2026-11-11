import java.util.ArrayList;
import java.util.List;

/**
 * Matches recipe ingredients against usable pantry inventory.
 */
public class RecipeMatcher {

    /**
     * Returns ingredients that are unavailable in the pantry.
     * Expired inventory is excluded by Pantry.getAvailableQuantity().
     *
     * Note: Recipe currently stores ingredient names rather than
     * quantities, so a positive amount in any unit counts as available.
     */
    public List<String> getMissingIngredients(Recipe recipe, Pantry pantry) {
        if (recipe == null || pantry == null) {
            throw new IllegalArgumentException(
                "Recipe and pantry cannot be null"
            );
        }

        List<String> missing = new ArrayList<>();

        for (String ingredient : recipe.getIngredients()) {
            boolean available = false;

            for (PantryItem item : pantry.getItems()) {
                if (item.getIngredientName().equalsIgnoreCase(ingredient)
                        && pantry.getAvailableQuantity(
                            item.getIngredientName(), item.getUnit()) > 0) {
                    available = true;
                    break;
                }
            }

            if (!available) {
                missing.add(ingredient);
            }
        }

        return missing;
    }

    /**
     * Returns the proportion of recipe ingredients available.
     */
    public double getMatchPercentage(Recipe recipe, Pantry pantry) {
        int total = recipe.getIngredients().size();
        int missing = getMissingIngredients(recipe, pantry).size();

        return 100.0 * (total - missing) / total;
    }
}
