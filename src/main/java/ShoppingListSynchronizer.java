
import java.util.List;

public class ShoppingListSynchronizer implements MealPlanObserver {

    private final Pantry pantry;
    private final ShoppingListService service;
    private ShoppingList currentShoppingList =
        new ShoppingList(List.of());

    public ShoppingListSynchronizer(
            MealPlan plan,
            Pantry pantry,
            ShoppingListService service) {

        if (plan == null || pantry == null || service == null) {
            throw new IllegalArgumentException(
                "Plan, pantry and service are required");
        }

        this.pantry = pantry;
        this.service = service;

        currentShoppingList = service.build(plan, pantry);
        plan.attach(this);
    }

    @Override
    public void onMealPlanChanged(MealPlan plan) {
        currentShoppingList = service.build(plan, pantry);
    }

    public ShoppingList getCurrentShoppingList() {
        return currentShoppingList;
    }

    // Call this after a pantry change.
    public void refresh(MealPlan plan) {
        onMealPlanChanged(plan);
    }
}
