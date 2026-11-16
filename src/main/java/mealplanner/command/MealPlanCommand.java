package mealplanner.command;

/**
 * <b>Command pattern - Command interface.</b> An undoable change to a meal
 * plan. Separating "what to change" (decided by the AI agent) from "how the
 * change is applied and reverted" makes AI-driven edits consistent and
 * reversible.
 */
public interface MealPlanCommand {

    /** Applies the change to the receiver (a MealPlan). */
    void execute();

    /** Reverts a previously executed change. */
    void undo();

    /** Human-readable description for history and UI messages. */
    String describe();
}
