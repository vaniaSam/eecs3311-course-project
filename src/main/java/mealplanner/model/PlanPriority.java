package mealplanner.model;

import java.util.Locale;

/** Planning priority chosen by the user; selects the ranking Strategy (Stage 1 F06). */
public enum PlanPriority {
    BUDGET_FIRST,
    PANTRY_FIRST;

    /** Accepts "budget", "budget_first", "pantry", "pantry-first" etc. */
    public static PlanPriority parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Priority is required (budget or pantry)");
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("budget")) {
            return BUDGET_FIRST;
        }
        if (t.startsWith("pantry")) {
            return PANTRY_FIRST;
        }
        throw new IllegalArgumentException("Unknown priority: " + text + " (use budget or pantry)");
    }
}
