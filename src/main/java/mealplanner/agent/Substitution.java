package mealplanner.agent;

/**
 * One proposed ingredient substitute (F08).
 *
 * @param name       substitute ingredient
 * @param note       how to use it (ratio, flavour change)
 * @param inPantry   true if usable stock is in the pantry
 * @param acceptable false if it conflicts with the user's restrictions or dislikes
 * @param problem    reason it is not acceptable, or empty
 */
public record Substitution(String name, String note, boolean inPantry, boolean acceptable, String problem) {

    /** One-line rendering. */
    public String describe() {
        StringBuilder sb = new StringBuilder(name);
        if (!note.isBlank()) {
            sb.append(" - ").append(note);
        }
        if (inPantry) {
            sb.append(" [in your pantry]");
        }
        if (!acceptable) {
            sb.append(" [NOT SUITABLE: ").append(problem).append(']');
        }
        return sb.toString();
    }
}
