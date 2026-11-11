
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ShoppingListService {

    public ShoppingList build(MealPlan plan, Pantry pantry) {
        if (plan == null || pantry == null) {
            throw new IllegalArgumentException(
                "Meal plan and pantry are required");
        }

        List<IngredientRequirement> allRequirements =
            new ArrayList<>();

        for (PlannedMeal meal : plan.getMeals()) {
            Recipe recipe = meal.getRecipe();

            if (recipe.getIngredientRequirements().isEmpty()) {
                throw new IllegalStateException(
                    "Missing ingredient quantities for recipe: "
                    + recipe.getName());
            }

            allRequirements.addAll(
                recipe.getIngredientRequirements());
        }

        List<IngredientRequirement> consolidated =
            consolidate(allRequirements);

        List<ShoppingListItem> missing = new ArrayList<>();

        for (IngredientRequirement requirement : consolidated) {
            ShoppingListItem item = missingFor(
                requirement, pantry);

            if (item != null) {
                missing.add(item);
            }
        }

        return new ShoppingList(missing);
    }

    public ShoppingListItem missingFor(
            IngredientRequirement requirement,
            Pantry pantry) {

        if (requirement == null || pantry == null) {
            throw new IllegalArgumentException(
                "Ingredient requirement and pantry are required");
        }

        double available = pantry.getAvailableQuantity(
            requirement.getName(),
            requirement.getUnit());

        double deficit = requirement.getQuantity() - available;

        if (deficit <= 0.000001) {
            return null;
        }

        return new ShoppingListItem(
            requirement.getName(),
            deficit,
            requirement.getUnit());
    }

    public List<IngredientRequirement> consolidate(
            List<IngredientRequirement> requirements) {

        if (requirements == null) {
            throw new IllegalArgumentException(
                "Requirements cannot be null");
        }

        Map<String, IngredientRequirement> combined =
            new LinkedHashMap<>();

        for (IngredientRequirement requirement : requirements) {
            if (requirement == null) {
                throw new IllegalArgumentException(
                    "Ingredient cannot be null");
            }

            String key = requirement.getName()
                .trim().toLowerCase(Locale.ROOT)
                + "\u0000"
                + requirement.getUnit()
                .trim().toLowerCase(Locale.ROOT);

            IngredientRequirement existing = combined.get(key);

            if (existing == null) {
                combined.put(key, requirement);
            } else {
                combined.put(key, new IngredientRequirement(
                    existing.getName(),
                    existing.getQuantity()
                        + requirement.getQuantity(),
                    existing.getUnit()
                ));
            }
        }

        return new ArrayList<>(combined.values());
    }
}
