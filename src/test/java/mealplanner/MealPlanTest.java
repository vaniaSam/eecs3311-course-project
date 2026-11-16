package mealplanner;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;

import org.junit.jupiter.api.Test;

import mealplanner.model.MealPlan;
import mealplanner.model.MealPlanObserver;
import mealplanner.model.PlannedMeal;
import mealplanner.model.PlannedMeal.MealType;
import mealplanner.model.Recipe;

/** MealPlan as Observer subject (ported from the earlier main-based test, plus new API). */
class MealPlanTest {

    private final Recipe oatmeal = Recipe.builder("Oatmeal").ingredient("Oats", 50, "g")
        .ingredient("Milk", 200, "ml").servings(1).estimatedCost(1).build();
    private final Recipe chickenRice = Recipe.builder("Chicken Rice").ingredient("Chicken", 300, "g")
        .ingredient("Rice", 150, "g").servings(2).estimatedCost(8).build();

    @Test
    void observerIsNotifiedOnEveryChangeOnly() {
        MealPlan plan = new MealPlan();
        int[] notifications = {0};
        MealPlanObserver observer = changed -> notifications[0]++;
        plan.attach(observer);
        plan.attach(observer); // duplicate attach is ignored

        plan.addOrReplaceMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.BREAKFAST, oatmeal));
        plan.addOrReplaceMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, chickenRice));
        assertEquals(2, plan.getScheduledMealCount());
        assertEquals(2, notifications[0]);

        plan.addOrReplaceMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.BREAKFAST, chickenRice));
        assertEquals(2, plan.getScheduledMealCount(), "replacement does not create a duplicate slot");
        assertSame(chickenRice, plan.getMeal(DayOfWeek.MONDAY, MealType.BREAKFAST).getRecipe());
        assertEquals(3, notifications[0]);

        assertTrue(plan.removeMeal(DayOfWeek.MONDAY, MealType.DINNER));
        assertEquals(4, notifications[0]);
        assertFalse(plan.removeMeal(DayOfWeek.TUESDAY, MealType.LUNCH));
        assertEquals(4, notifications[0], "removing an empty slot does not notify");

        plan.detach(observer);
        plan.addOrReplaceMeal(new PlannedMeal(DayOfWeek.FRIDAY, MealType.DINNER, oatmeal));
        assertEquals(4, notifications[0], "detached observer is not notified");
    }

    @Test
    void replaceAndAddMealUseExactObjects() {
        MealPlan plan = new MealPlan();
        PlannedMeal monday = new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, oatmeal);
        plan.addMeal(monday);
        assertThrows(IllegalStateException.class,
            () -> plan.addMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, chickenRice)));
        PlannedMeal replacement = new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, chickenRice);
        plan.replaceMeal(monday, replacement);
        assertSame(replacement, plan.getMeal(DayOfWeek.MONDAY, MealType.DINNER));
        assertThrows(IllegalArgumentException.class, () -> plan.replaceMeal(monday, replacement));
    }

    @Test
    void costUsesPlannedServings() {
        MealPlan plan = new MealPlan();
        plan.addMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, chickenRice, 4));
        assertEquals(16.0, plan.getEstimatedCost(), 1e-9);
        assertThrows(IllegalArgumentException.class,
            () -> new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, chickenRice, 0));
    }

    @Test
    void mealsAreSortedByDayThenType() {
        MealPlan plan = new MealPlan();
        plan.addMeal(new PlannedMeal(DayOfWeek.TUESDAY, MealType.DINNER, oatmeal));
        plan.addMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, oatmeal));
        plan.addMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.BREAKFAST, oatmeal));
        assertEquals(MealType.BREAKFAST, plan.getMeals().get(0).getMealType());
        assertEquals(DayOfWeek.TUESDAY, plan.getMeals().get(2).getDay());
        assertTrue(plan.describe().contains("MONDAY"));
    }
}
