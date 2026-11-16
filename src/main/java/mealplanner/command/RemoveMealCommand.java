package mealplanner.command;

import java.util.Objects;

import mealplanner.model.MealPlan;
import mealplanner.model.PlannedMeal;

/** <b>Command pattern - Concrete Command.</b> Removes a planned meal; undo puts it back in its slot. */
public class RemoveMealCommand implements MealPlanCommand {

    private final MealPlan receiver;
    private final PlannedMeal removedMeal;

    public RemoveMealCommand(MealPlan receiver, PlannedMeal removedMeal) {
        this.receiver = Objects.requireNonNull(receiver, "Plan is required");
        this.removedMeal = Objects.requireNonNull(removedMeal, "Meal to remove is required");
    }

    @Override
    public void execute() {
        if (!receiver.removeMeal(removedMeal)) {
            throw new IllegalStateException("Meal is not part of the plan: " + removedMeal);
        }
    }

    @Override
    public void undo() {
        receiver.addMeal(removedMeal);
    }

    @Override
    public String describe() {
        return "Remove " + removedMeal.getDay() + " " + removedMeal.getMealType() + ": "
            + removedMeal.getRecipe().getName();
    }
}
