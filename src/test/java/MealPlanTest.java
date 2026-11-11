
import java.time.DayOfWeek;
import java.util.List;

public class MealPlanTest {

    public static void main(String[] args) {

        Recipe oatmeal = new Recipe(
            "Oatmeal",
            List.of("Oats", "Milk"),
            List.of("Cook oats in milk"),
            10, 1
        );

        Recipe chickenRice = new Recipe(
            "Chicken Rice",
            List.of("Chicken", "Rice"),
            List.of("Cook and serve"),
            30, 2
        );

        MealPlan plan = new MealPlan();

        // Count observer notifications.
        final int[] notifications = {0};

        MealPlanObserver observer = changedPlan -> {
            notifications[0]++;
        };

        plan.attach(observer);

        plan.addOrReplaceMeal(new PlannedMeal(
            DayOfWeek.MONDAY,
            PlannedMeal.MealType.BREAKFAST,
            oatmeal
        ));

        plan.addOrReplaceMeal(new PlannedMeal(
            DayOfWeek.MONDAY,
            PlannedMeal.MealType.DINNER,
            chickenRice
        ));

        if (plan.getScheduledMealCount() != 2) {
            throw new AssertionError("Meal scheduling failed");
        }

        if (notifications[0] != 2) {
            throw new AssertionError("Observer was not notified");
        }

        // Replace breakfast without creating a duplicate slot.
        plan.addOrReplaceMeal(new PlannedMeal(
            DayOfWeek.MONDAY,
            PlannedMeal.MealType.BREAKFAST,
            chickenRice
        ));

        if (plan.getScheduledMealCount() != 2
                || plan.getMeal(
                    DayOfWeek.MONDAY,
                    PlannedMeal.MealType.BREAKFAST
                ).getRecipe() != chickenRice) {
            throw new AssertionError("Meal replacement failed");
        }

        if (notifications[0] != 3) {
            throw new AssertionError(
                "Observer missed meal replacement"
            );
        }

        boolean removed = plan.removeMeal(
            DayOfWeek.MONDAY,
            PlannedMeal.MealType.DINNER
        );

        if (!removed || plan.getScheduledMealCount() != 1) {
            throw new AssertionError("Meal removal failed");
        }

        if (notifications[0] != 4) {
            throw new AssertionError(
                "Observer missed meal removal"
            );
        }

        // Removing an empty slot should not notify observers.
        plan.removeMeal(
            DayOfWeek.TUESDAY,
            PlannedMeal.MealType.LUNCH
        );

        if (notifications[0] != 4) {
            throw new AssertionError(
                "Unexpected observer notification"
            );
        }

        // Detached observers should receive no further updates.
        plan.detach(observer);

        plan.addOrReplaceMeal(new PlannedMeal(
            DayOfWeek.FRIDAY,
            PlannedMeal.MealType.DINNER,
            oatmeal
        ));

        if (notifications[0] != 4) {
            throw new AssertionError(
                "Detached observer was notified"
            );
        }

        System.out.println("Meal plan observer test passed!");
    }
}
