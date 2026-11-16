package mealplanner.model;

import java.util.Objects;

/**
 * Structured result of interpreting a natural-language meal-plan instruction
 * (Stage 1 F11). It connects AI interpretation to the Command pattern: the
 * facade turns a PlanEdit into a concrete command.
 *
 * @param operation       REPLACE or REMOVE
 * @param targetMeal      the meal in the current plan to change
 * @param replacementMeal the new meal for REPLACE; {@code null} for REMOVE
 */
public record PlanEdit(EditOperation operation, PlannedMeal targetMeal, PlannedMeal replacementMeal) {

    public PlanEdit {
        Objects.requireNonNull(operation, "Operation is required");
        Objects.requireNonNull(targetMeal, "Target meal is required");
        if (operation == EditOperation.REPLACE && replacementMeal == null) {
            throw new IllegalArgumentException("A REPLACE edit needs a replacement meal");
        }
        if (operation == EditOperation.REMOVE && replacementMeal != null) {
            throw new IllegalArgumentException("A REMOVE edit cannot have a replacement meal");
        }
    }

    /** Short human-readable description. */
    public String describe() {
        return operation == EditOperation.REMOVE
            ? "Remove " + targetMeal
            : "Replace " + targetMeal + " with " + replacementMeal.getRecipe().getName();
    }
}
