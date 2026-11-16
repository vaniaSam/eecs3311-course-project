package mealplanner.service;

import java.util.List;

import mealplanner.model.MealPlan;
import mealplanner.model.MealPlanObserver;
import mealplanner.model.ShoppingList;

/**
 * <b>Observer pattern - Concrete Observer.</b> Keeps the current shopping list
 * in step with the meal plan (Stage 1 F10/F11). When the plan notifies it, it
 * recalculates the list through {@link ShoppingListService} using the latest
 * pantry contents, so MealPlan never needs to know about shopping lists.
 */
public class ShoppingListSynchronizer implements MealPlanObserver {

    private final PantryService pantryService;
    private final ShoppingListService service;
    private MealPlan trackedPlan;
    private ShoppingList currentShoppingList = new ShoppingList(List.of());
    private int refreshCount;

    public ShoppingListSynchronizer(PantryService pantryService, ShoppingListService service) {
        if (pantryService == null || service == null) {
            throw new IllegalArgumentException("Pantry service and shopping-list service are required");
        }
        this.pantryService = pantryService;
        this.service = service;
    }

    /**
     * Starts observing {@code plan}, detaching from any previously tracked
     * plan, and computes the list immediately.
     */
    public void track(MealPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Plan is required");
        }
        if (trackedPlan != null) {
            trackedPlan.detach(this);
        }
        trackedPlan = plan;
        plan.attach(this);
        onMealPlanChanged(plan);
    }

    @Override
    public void onMealPlanChanged(MealPlan plan) {
        currentShoppingList = service.build(plan, pantryService.getPantry());
        refreshCount++;
    }

    /** Recalculates after a pantry change (the pantry is not an observable subject). */
    public void refresh() {
        if (trackedPlan != null) {
            onMealPlanChanged(trackedPlan);
        }
    }

    public ShoppingList getCurrentShoppingList() {
        return currentShoppingList;
    }

    /** Number of recalculations so far - lets tests and the UI show that observer updates happened. */
    public int getRefreshCount() {
        return refreshCount;
    }
}
