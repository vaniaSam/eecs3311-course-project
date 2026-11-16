package mealplanner.controller;

import mealplanner.agent.EditInterpretation;
import mealplanner.model.MealPlan;

/**
 * Outcome of F11 natural-language plan modification.
 *
 * @param applied        true if a command was executed
 * @param message        what happened, or why nothing changed
 * @param plan           the current plan after the operation (may be {@code null})
 * @param interpretation the agent's interpretation, including its trace (may be {@code null})
 */
public record PlanEditResult(boolean applied, String message, MealPlan plan, EditInterpretation interpretation) {
}
