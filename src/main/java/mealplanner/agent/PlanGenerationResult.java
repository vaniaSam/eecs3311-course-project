package mealplanner.agent;

import mealplanner.model.MealPlan;
import mealplanner.model.ValidationResult;

/**
 * Result of F06 weekly meal-plan generation.
 *
 * @param plan       the proposed plan (may be {@code null} if nothing could be assembled)
 * @param validation final validation of the plan
 * @param aiUsed     true if the accepted plan was assembled by the LLM
 * @param rationale  LLM rationale or fallback explanation
 * @param trace      agent steps, including revisions
 */
public record PlanGenerationResult(MealPlan plan, ValidationResult validation, boolean aiUsed, String rationale,
                                   AgentTrace trace) {

    /** A plan is accepted only if it exists and passed validation. */
    public boolean accepted() {
        return plan != null && validation.isValid();
    }
}
