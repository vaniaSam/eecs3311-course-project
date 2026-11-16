package mealplanner.agent;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import mealplanner.model.IngredientRequirement;
import mealplanner.model.MealPlan;
import mealplanner.model.PlanRequest;
import mealplanner.model.PlannedMeal;
import mealplanner.model.PlannedMeal.MealType;
import mealplanner.model.Quantities;
import mealplanner.model.Recipe;
import mealplanner.model.UserProfile;
import mealplanner.model.ValidationResult;

/**
 * Deterministic constraint checks applied to everything the agent proposes
 * (Stage 1 PlanValidator). LLM output is only accepted after it passes these
 * checks; failures are fed back to the LLM so it can revise its proposal.
 */
public class PlanValidator {

    /** A recipe may appear at most this many times in one plan. */
    public static final int MAX_REPEATS = 2;

    /**
     * Checks a recipe against the profile's restrictions and disliked ingredients.
     * A weekly budget of 0 means "no budget limit".
     */
    public ValidationResult checkRecipe(Recipe recipe, UserProfile profile) {
        ValidationResult result = new ValidationResult();
        if (recipe == null) {
            result.addError("No recipe was provided");
            return result;
        }
        for (String restriction : profile.getRestrictions()) {
            if (!DietaryRules.isKnown(restriction)) {
                result.addWarning("'" + restriction + "' is not a built-in restriction; it was checked as an "
                    + "ingredient to avoid");
            }
            for (IngredientRequirement r : recipe.getIngredientRequirements()) {
                if (DietaryRules.violates(restriction, r.getName())) {
                    result.addError(recipe.getName() + " is not " + restriction + ": contains " + r.getName());
                }
            }
        }
        for (String disliked : profile.getDislikedIngredients()) {
            for (IngredientRequirement r : recipe.getIngredientRequirements()) {
                if (DietaryRules.containsWord(r.getName().toLowerCase(Locale.ROOT),
                        DietaryRules.customKeyword(disliked))) {
                    result.addError(recipe.getName() + " contains disliked ingredient " + r.getName());
                }
            }
        }
        if (recipe.getSource() == Recipe.Source.AI_GENERATED) {
            result.addWarning("AI-generated recipe: ingredients were checked by keyword only; verify allergens "
                + "yourself");
        }
        return result;
    }

    /** Stage 1 convenience signature returning only validity. */
    public boolean isAcceptable(Recipe recipe, UserProfile profile) {
        return checkRecipe(recipe, profile).isValid();
    }

    /**
     * Validates a whole plan against the request: every slot filled, every
     * recipe acceptable, limited repetition, and the prorated weekly budget.
     */
    public ValidationResult validate(MealPlan plan, PlanRequest request) {
        ValidationResult result = new ValidationResult();
        if (plan == null) {
            result.addError("No plan was produced");
            return result;
        }
        DayOfWeek first = request.getWeekStart().getDayOfWeek();
        for (int d = 0; d < request.getDays(); d++) {
            DayOfWeek day = first.plus(d);
            for (MealType type : request.getMealTypes()) {
                if (plan.getMeal(day, type) == null) {
                    result.addError("Missing meal for " + day + " " + type);
                }
            }
        }
        Map<String, Integer> counts = new HashMap<>();
        for (PlannedMeal meal : plan.getMeals()) {
            Recipe recipe = meal.getRecipe();
            ValidationResult rr = checkRecipe(recipe, request.getProfile());
            rr.getErrors().forEach(e -> result.addError(meal.getDay() + " " + meal.getMealType() + ": " + e));
            counts.merge(recipe.getId(), 1, Integer::sum);
            boolean breakfastDish = recipe.hasTag("breakfast");
            if (breakfastDish != (meal.getMealType() == MealType.BREAKFAST)) {
                result.addWarning(recipe.getName() + " is unusual for " + meal.getMealType().name()
                    .toLowerCase(Locale.ROOT));
            }
        }
        counts.forEach((id, n) -> {
            if (n > MAX_REPEATS) {
                result.addError("Recipe " + id + " is used " + n + " times (max " + MAX_REPEATS + ")");
            }
        });
        double budget = request.getProfile().getWeeklyBudget() * request.getDays() / 7.0;
        if (budget > 0 && plan.getEstimatedCost() > budget + 1e-9) {
            result.addError("Estimated cost " + Quantities.money(plan.getEstimatedCost())
                + " exceeds the budget of " + Quantities.money(budget) + " for " + request.getDays() + " day(s)");
        }
        return result;
    }
}
