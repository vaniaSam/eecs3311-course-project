package mealplanner.agent;

import mealplanner.model.PlanEdit;

/**
 * Result of interpreting a natural-language edit (F11).
 *
 * @param edit    structured edit, or {@code null} if the instruction was rejected or ambiguous
 * @param message explanation or clarification request
 * @param aiUsed  true if the LLM interpreted the text; false if the rule-based parser did
 * @param trace   agent steps
 */
public record EditInterpretation(PlanEdit edit, String message, boolean aiUsed, AgentTrace trace) {
}
