package mealplanner.command;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * <b>Command pattern - Invoker.</b> Executes meal-plan commands and keeps a
 * history stack so the most recent change can be undone.
 */
public class CommandExecutor {

    private final Deque<MealPlanCommand> history = new ArrayDeque<>();

    /** Executes the command; it is recorded only if execution succeeds. */
    public void execute(MealPlanCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command is required");
        }
        command.execute();
        history.push(command);
    }

    /** Undoes the most recent command, if any, and returns it. */
    public Optional<MealPlanCommand> undoLast() {
        if (history.isEmpty()) {
            return Optional.empty();
        }
        MealPlanCommand command = history.pop();
        command.undo();
        return Optional.of(command);
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    /** Forgets history, e.g. when a brand-new plan replaces the old one. */
    public void clear() {
        history.clear();
    }

    /** Most recent first. */
    public List<String> historyDescriptions() {
        List<String> out = new ArrayList<>();
        history.forEach(c -> out.add(c.describe()));
        return out;
    }
}
