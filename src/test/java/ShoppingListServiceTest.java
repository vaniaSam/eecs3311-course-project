
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public class ShoppingListServiceTest {

    public static void main(String[] args) {
        Pantry pantry = new Pantry();

        pantry.addItem(new PantryItem(
            "Rice", 0.5, "kg",
            LocalDate.now().plusDays(30),
            PantryItem.ItemKind.INGREDIENT,
            "Cupboard"
        ));

        Recipe riceMeal = Recipe.withRequirements(
            "Rice Meal",
            List.of(
                new IngredientRequirement("Rice", 1.0, "kg"),
                new IngredientRequirement("Chicken", 0.4, "kg")
            ),
            List.of("Cook and serve"),
            30, 2
        );

        Recipe riceSoup = Recipe.withRequirements(
            "Rice Soup",
            List.of(
                new IngredientRequirement("rice", 0.5, "kg"),
                new IngredientRequirement("Carrot", 0.2, "kg")
            ),
            List.of("Boil ingredients"),
            25, 2
        );

        MealPlan plan = new MealPlan();

        ShoppingListSynchronizer synchronizer =
            new ShoppingListSynchronizer(
                plan, pantry, new ShoppingListService()
            );

        plan.addOrReplaceMeal(new PlannedMeal(
            DayOfWeek.MONDAY,
            PlannedMeal.MealType.DINNER,
            riceMeal
        ));

        ShoppingList first =
            synchronizer.getCurrentShoppingList();

        assertQuantity(first, "Rice", "kg", 0.5);
        assertQuantity(first, "Chicken", "kg", 0.4);

        // Both meals need rice. Pantry stock is subtracted once.
        plan.addOrReplaceMeal(new PlannedMeal(
            DayOfWeek.TUESDAY,
            PlannedMeal.MealType.LUNCH,
            riceSoup
        ));

        ShoppingList combined =
            synchronizer.getCurrentShoppingList();

        if (combined.size() != 3) {
            throw new AssertionError(
                "Duplicate ingredients were not consolidated");
        }

        assertQuantity(combined, "Rice", "kg", 1.0);
        assertQuantity(combined, "Chicken", "kg", 0.4);
        assertQuantity(combined, "Carrot", "kg", 0.2);

        // Removing a meal triggers an automatic update.
        plan.removeMeal(
            DayOfWeek.MONDAY,
            PlannedMeal.MealType.DINNER
        );

        ShoppingList updated =
            synchronizer.getCurrentShoppingList();

        if (updated.size() != 1) {
            throw new AssertionError(
                "Shopping list did not update after removal");
        }

        assertQuantity(updated, "Carrot", "kg", 0.2);

        System.out.println(
            "Shopping list synchronization test passed!");
    }

    private static void assertQuantity(
            ShoppingList list,
            String name,
            String unit,
            double expected) {

        for (ShoppingListItem item : list.getItems()) {
            if (item.getName().equalsIgnoreCase(name)
                    && item.getUnit().equalsIgnoreCase(unit)) {

                if (Math.abs(item.getQuantity() - expected) > 0.000001) {
                    throw new AssertionError(
                        "Incorrect quantity for " + name);
                }
                return;
            }
        }

        throw new AssertionError("Missing item: " + name);
    }
}
