package mealplanner.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import mealplanner.model.IngredientRequirement;
import mealplanner.model.MealPlan;
import mealplanner.model.Pantry;
import mealplanner.model.PantryItem;
import mealplanner.model.PlannedMeal;
import mealplanner.model.Quantities;
import mealplanner.model.Recipe;
import mealplanner.model.ShoppingList;
import mealplanner.model.ShoppingListItem;

/**
 * Deterministic shopping-list calculation (Stage 1 F10): collects the
 * requirements of every planned meal (scaled to the planned servings),
 * consolidates duplicates, and subtracts usable pantry stock.
 */
public class ShoppingListService {

    /** Builds the consolidated list of missing ingredients for a plan. */
    public ShoppingList build(MealPlan plan, Pantry pantry) {
        if (plan == null || pantry == null) {
            throw new IllegalArgumentException("Meal plan and pantry are required");
        }
        List<IngredientRequirement> all = new ArrayList<>();
        for (PlannedMeal meal : plan.getMeals()) {
            Recipe recipe = meal.getRecipe();
            Recipe scaled = meal.getServings() == recipe.getServings() ? recipe : recipe.scaleTo(meal.getServings());
            all.addAll(scaled.getIngredientRequirements());
        }
        List<ShoppingListItem> missing = new ArrayList<>();
        for (IngredientRequirement requirement : consolidate(all)) {
            ShoppingListItem item = missingFor(requirement, pantry);
            if (item != null) {
                missing.add(item);
            }
        }
        return new ShoppingList(missing);
    }

    /** Builds a shopping list for a single recipe (used by recipe views and the assistant). */
    public ShoppingList buildForRecipe(Recipe recipe, Pantry pantry) {
        List<ShoppingListItem> missing = new ArrayList<>();
        for (IngredientRequirement r : recipe.getIngredientRequirements()) {
            ShoppingListItem item = missingFor(r, pantry);
            if (item != null) {
                missing.add(item);
            }
        }
        return new ShoppingList(missing);
    }

    /**
     * The deficit for one requirement, or {@code null} if the pantry covers it.
     * If the pantry holds the ingredient only in a different unit the deficit
     * is still listed, with a note, instead of guessing a conversion.
     */
    public ShoppingListItem missingFor(IngredientRequirement requirement, Pantry pantry) {
        if (requirement == null || pantry == null) {
            throw new IllegalArgumentException("Ingredient requirement and pantry are required");
        }
        double available = pantry.getAvailableQuantity(requirement.getName(), requirement.getUnit());
        double deficit = requirement.getQuantity() - available;
        if (deficit <= 0.000001) {
            return null;
        }
        String note = "";
        for (PantryItem item : pantry.findItems(requirement.getName())) {
            if (!item.getUnit().equalsIgnoreCase(requirement.getUnit()) && !item.isExpired()) {
                note = "pantry has " + Quantities.format(item.getAmount()) + " " + item.getUnit()
                    + " - different unit, not converted";
                break;
            }
        }
        return new ShoppingListItem(requirement.getName(), deficit, requirement.getUnit(), note);
    }

    /** Merges requirements with the same name and unit by summing quantities. */
    public List<IngredientRequirement> consolidate(List<IngredientRequirement> requirements) {
        if (requirements == null) {
            throw new IllegalArgumentException("Requirements cannot be null");
        }
        Map<String, IngredientRequirement> combined = new LinkedHashMap<>();
        for (IngredientRequirement requirement : requirements) {
            if (requirement == null) {
                throw new IllegalArgumentException("Ingredient cannot be null");
            }
            combined.merge(requirement.mergeKey(), requirement, (a, b) ->
                new IngredientRequirement(a.getName(), a.getQuantity() + b.getQuantity(), a.getUnit()));
        }
        return new ArrayList<>(combined.values());
    }
}
