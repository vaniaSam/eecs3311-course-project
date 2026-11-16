package mealplanner;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import mealplanner.model.MealPlan;
import mealplanner.model.Pantry;
import mealplanner.model.PantryItem;
import mealplanner.model.PlannedMeal;
import mealplanner.model.PlannedMeal.MealType;
import mealplanner.model.Recipe;
import mealplanner.model.ShoppingList;
import mealplanner.model.ShoppingListItem;
import mealplanner.service.PantryService;
import mealplanner.service.ShoppingListService;
import mealplanner.service.ShoppingListSynchronizer;

/** F10 shopping-list generation and Observer-based synchronisation. */
class ShoppingListServiceTest {

    private final Recipe riceMeal = Recipe.builder("Rice Meal")
        .ingredient("Rice", 1.0, "kg").ingredient("Chicken", 0.4, "kg").servings(2).build();
    private final Recipe riceSoup = Recipe.builder("Rice Soup")
        .ingredient("rice", 0.5, "kg").ingredient("Carrot", 0.2, "kg").servings(2).build();

    private static Pantry pantryWithRice() {
        Pantry pantry = new Pantry();
        pantry.addItem(new PantryItem("Rice", 0.5, "kg", LocalDate.now().plusDays(30),
            PantryItem.ItemKind.INGREDIENT, "Cupboard"));
        return pantry;
    }

    @Test
    void synchronizerConsolidatesAndTracksPlanChanges() {
        Pantry pantry = pantryWithRice();
        MealPlan plan = new MealPlan();
        ShoppingListSynchronizer sync = new ShoppingListSynchronizer(new PantryService(pantry),
            new ShoppingListService());
        sync.track(plan);

        plan.addOrReplaceMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, riceMeal));
        assertQuantity(sync.getCurrentShoppingList(), "Rice", "kg", 0.5);
        assertQuantity(sync.getCurrentShoppingList(), "Chicken", "kg", 0.4);

        plan.addOrReplaceMeal(new PlannedMeal(DayOfWeek.TUESDAY, MealType.LUNCH, riceSoup));
        ShoppingList combined = sync.getCurrentShoppingList();
        assertEquals(3, combined.size(), "duplicates consolidated");
        assertQuantity(combined, "Rice", "kg", 1.0);
        assertQuantity(combined, "Carrot", "kg", 0.2);

        plan.removeMeal(DayOfWeek.MONDAY, MealType.DINNER);
        assertEquals(1, sync.getCurrentShoppingList().size());
        assertQuantity(sync.getCurrentShoppingList(), "Carrot", "kg", 0.2);
    }

    @Test
    void trackingANewPlanDetachesTheOldOne() {
        ShoppingListSynchronizer sync = new ShoppingListSynchronizer(new PantryService(new Pantry()),
            new ShoppingListService());
        MealPlan first = new MealPlan();
        MealPlan second = new MealPlan();
        sync.track(first);
        sync.track(second);
        int before = sync.getRefreshCount();
        first.addMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, riceMeal));
        assertEquals(before, sync.getRefreshCount(), "old plan no longer observed");
        second.addMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, riceMeal));
        assertEquals(before + 1, sync.getRefreshCount());
    }

    @Test
    void plannedServingsScaleTheList() {
        MealPlan plan = new MealPlan();
        plan.addMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, riceSoup, 4));
        ShoppingList list = new ShoppingListService().build(plan, new Pantry());
        assertQuantity(list, "rice", "kg", 1.0);
        assertQuantity(list, "Carrot", "kg", 0.4);
    }

    @Test
    void differentUnitInPantryIsFlaggedNotConverted() {
        Pantry pantry = new Pantry();
        pantry.addItem(new PantryItem("Carrot", 3, "pcs", null, PantryItem.ItemKind.INGREDIENT, ""));
        MealPlan plan = new MealPlan();
        plan.addMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, riceSoup));
        ShoppingListItem carrot = find(new ShoppingListService().build(plan, pantry), "Carrot");
        assertTrue(carrot.getNote().contains("different unit"));
    }

    @Test
    void emptyPlanGivesEmptyList() {
        assertTrue(new ShoppingListService().build(new MealPlan(), new Pantry()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ShoppingListService().build(null, new Pantry()));
    }

    private static ShoppingListItem find(ShoppingList list, String name) {
        return list.getItems().stream().filter(i -> i.getName().equalsIgnoreCase(name)).findFirst()
            .orElseThrow(() -> new AssertionError("Missing item " + name));
    }

    private static void assertQuantity(ShoppingList list, String name, String unit, double expected) {
        ShoppingListItem item = find(list, name);
        assertEquals(unit, item.getUnit());
        assertEquals(expected, item.getQuantity(), 1e-6);
    }
}
