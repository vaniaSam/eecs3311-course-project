package mealplanner.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered log of the steps the agent took (ranking, LLM calls, validation
 * results, revisions, fallbacks). Shown in the GUI/CLI so users can see the
 * agent's reasoning instead of a single opaque answer.
 */
public class AgentTrace {
    private final List<String> steps = new ArrayList<>();

    public void add(String step) {
        steps.add(step);
    }

    public List<String> getSteps() {
        return List.copyOf(steps);
    }

    /** Numbered multi-line rendering. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            sb.append(i + 1).append(". ").append(steps.get(i)).append('\n');
        }
        return sb.toString().trim();
    }
}
