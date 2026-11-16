package mealplanner.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Everything the agent needs to plan or recommend: the profile, the planning
 * priority, how many days and which meal slots to fill, portions per meal and
 * an optional free-text instruction for the LLM.
 */
public class PlanRequest {
    private final UserProfile profile;
    private final LocalDate weekStart;
    private final PlanPriority priority;
    private final String userInstruction;
    private final int days;
    private final Set<PlannedMeal.MealType> mealTypes;
    private final int servingsPerMeal;

    public PlanRequest(UserProfile profile, LocalDate weekStart, PlanPriority priority, String userInstruction,
                       int days, Set<PlannedMeal.MealType> mealTypes, int servingsPerMeal) {
        this.profile = Objects.requireNonNull(profile, "Profile is required");
        this.weekStart = weekStart == null ? LocalDate.now() : weekStart;
        this.priority = priority == null ? PlanPriority.PANTRY_FIRST : priority;
        this.userInstruction = userInstruction == null ? "" : userInstruction.trim();
        this.days = days;
        this.mealTypes = mealTypes == null || mealTypes.isEmpty()
            ? EnumSet.of(PlannedMeal.MealType.DINNER) : EnumSet.copyOf(mealTypes);
        this.servingsPerMeal = servingsPerMeal;
    }

    /** Convenience request: 7 dinners for 2 people with the given priority. */
    public static PlanRequest simple(UserProfile profile, PlanPriority priority, String instruction) {
        return new PlanRequest(profile, LocalDate.now(), priority, instruction, 7,
            EnumSet.of(PlannedMeal.MealType.DINNER), 2);
    }

    /** Returns every validation problem; an empty list means the request is usable. */
    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        if (days < 1 || days > 7) {
            errors.add("Days must be between 1 and 7");
        }
        if (servingsPerMeal < 1 || servingsPerMeal > 20) {
            errors.add("Servings per meal must be between 1 and 20");
        }
        return errors;
    }

    /** Stage 1 API: true if {@link #validationErrors()} is empty. */
    public boolean validate() {
        return validationErrors().isEmpty();
    }

    public UserProfile getProfile() {
        return profile;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public PlanPriority getPriority() {
        return priority;
    }

    public String getUserInstruction() {
        return userInstruction;
    }

    public int getDays() {
        return days;
    }

    public Set<PlannedMeal.MealType> getMealTypes() {
        return EnumSet.copyOf(mealTypes);
    }

    public int getServingsPerMeal() {
        return servingsPerMeal;
    }

    /** Number of meal slots this request asks to fill. */
    public int slotCount() {
        return days * mealTypes.size();
    }
}
