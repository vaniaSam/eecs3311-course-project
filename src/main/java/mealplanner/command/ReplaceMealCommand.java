package mealplanner.command;

import java.util.Objects;

import mealplanner.model.MealPlan;
import mealplanner.model.PlannedMeal;

/** <b>Command pattern - Concrete Command.</b> Replaces one planned meal with another; undo restores the old meal. */
public class ReplaceMealCommand implements MealPlanCommand {

    private final MealPlan receiver;
    private final PlannedMeal oldMeal;
    private final PlannedMeal newMeal;

    public ReplaceMealCommand(MealPlan receiver, PlannedMeal oldMeal, PlannedMeal newMeal) {
        this.receiver = Objects.requireNonNull(receiver, "Plan is required");
        this.oldMeal = Objects.requireNonNull(oldMeal, "Meal to replace is required");
        this.newMeal = Objects.requireNonNull(newMeal, "Replacement meal is required");
        if (oldMeal.getDay() != newMeal.getDay() || oldMeal.getMealType() != newMeal.getMealType()) {
            throw new IllegalArgumentException("Replacement must use the same day and meal type");
        }
    }

    @Override
    public void execute() {
        receiver.replaceMeal(oldMeal, newMeal);
    }

    @Override
    public void undo() {
        receiver.replaceMeal(newMeal, oldMeal);
    }

    @Override
    public String describe() {
        return "Replace " + oldMeal.getDay() + " " + oldMeal.getMealType() + ": " + oldMeal.getRecipe().getName()
            + " -> " + newMeal.getRecipe().getName();
    }
}
