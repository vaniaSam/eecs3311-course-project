
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a weekly meal plan.
 *
 * Observer pattern:
 * MealPlan is the subject. Registered observers are notified
 * whenever a scheduled meal is added, replaced, or removed.
 */
public class MealPlan {

    private final List<PlannedMeal> meals = new ArrayList<>();
    private final List<MealPlanObserver> observers = new ArrayList<>();

    public void attach(MealPlanObserver observer) {
        Objects.requireNonNull(observer, "Observer cannot be null");

        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void detach(MealPlanObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers() {
        for (MealPlanObserver observer :
                new ArrayList<>(observers)) {
            observer.onMealPlanChanged(this);
        }
    }

    /**
     * Adds a meal or replaces an existing meal in the same slot.
     */
    public void addOrReplaceMeal(PlannedMeal meal) {
        Objects.requireNonNull(meal, "Meal cannot be null");

        meals.removeIf(existing ->
            existing.getDay() == meal.getDay()
                && existing.getMealType() == meal.getMealType()
        );

        meals.add(meal);
        notifyObservers();
    }

    /**
     * Removes the meal assigned to a day and meal type.
     * Returns true if a meal was removed.
     */
    public boolean removeMeal(
            DayOfWeek day,
            PlannedMeal.MealType mealType) {

        Objects.requireNonNull(day, "Day cannot be null");
        Objects.requireNonNull(mealType, "Meal type cannot be null");

        boolean removed = meals.removeIf(meal ->
            meal.getDay() == day
                && meal.getMealType() == mealType
        );

        if (removed) {
            notifyObservers();
        }

        return removed;
    }

    public PlannedMeal getMeal(
            DayOfWeek day,
            PlannedMeal.MealType mealType) {

        Objects.requireNonNull(day, "Day cannot be null");
        Objects.requireNonNull(mealType, "Meal type cannot be null");

        for (PlannedMeal meal : meals) {
            if (meal.getDay() == day
                    && meal.getMealType() == mealType) {
                return meal;
            }
        }

        return null;
    }

    public List<PlannedMeal> getMeals() {
        return Collections.unmodifiableList(
            new ArrayList<>(meals)
        );
    }

    public int getScheduledMealCount() {
        return meals.size();
    }
}
