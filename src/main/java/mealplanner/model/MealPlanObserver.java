package mealplanner.model;

/**
 * <b>Observer pattern - Observer role.</b> Implemented by components that must
 * react when a {@link MealPlan} (the Subject) changes, e.g. the shopping list.
 */
public interface MealPlanObserver {

    /** Called after any meal has been added, replaced or removed. */
    void onMealPlanChanged(MealPlan plan);
}
