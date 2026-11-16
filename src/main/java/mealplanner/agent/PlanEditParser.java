package mealplanner.agent;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mealplanner.model.EditOperation;
import mealplanner.model.MealPlan;
import mealplanner.model.PlanEdit;
import mealplanner.model.PlannedMeal;
import mealplanner.model.PlannedMeal.MealType;
import mealplanner.model.Recipe;
import mealplanner.model.UserProfile;
import mealplanner.service.RecipeService;

/**
 * Deterministic interpreter for simple edit phrases such as
 * "remove tuesday dinner" or "replace monday lunch with greek salad".
 * Used by the agent as a fallback when the LLM is unavailable, so F11 still
 * works for structured phrasing; free-form requests need the LLM.
 */
public final class PlanEditParser {

    private static final Pattern REMOVE = Pattern.compile("\\b(remove|delete|drop|skip|cancel|clear)\\b");
    private static final Pattern REPLACE = Pattern.compile("\\b(replace|swap|change|switch|substitute|make)\\b");
    private static final Pattern TARGET_TEXT = Pattern.compile("\\b(?:with|to|for|into|by)\\s+(.+)$");

    private PlanEditParser() {
    }

    /** Outcome: either an edit or an explanation of why the text could not be used. */
    public record Outcome(PlanEdit edit, String message) {
    }

    public static Outcome parse(String text, MealPlan plan, RecipeService recipes, UserProfile profile,
                                PlanValidator validator) {
        String t = text.toLowerCase(Locale.ROOT).trim();
        boolean remove = REMOVE.matcher(t).find();
        boolean replace = REPLACE.matcher(t).find() || t.contains(" instead");
        if (!remove && !replace) {
            return new Outcome(null, "Only REPLACE or REMOVE edits are supported, e.g. "
                + "\"replace Monday dinner with Greek Salad\" or \"remove Tuesday lunch\".");
        }
        Optional<PlannedMeal> target = findTarget(t, plan);
        if (target.isEmpty()) {
            return new Outcome(null, "Could not tell which planned meal to change. Mention the day and meal, "
                + "e.g. \"Tuesday dinner\".");
        }
        PlannedMeal meal = target.get();
        if (remove && !replace) {
            return new Outcome(new PlanEdit(EditOperation.REMOVE, meal, null), "Remove " + meal);
        }
        Matcher m = TARGET_TEXT.matcher(t);
        if (!m.find()) {
            return new Outcome(null, "Say what to replace the meal with, e.g. \"... with Chickpea Curry\".");
        }
        String wanted = m.group(1).replaceAll("[.!?]+$", "").trim();
        Optional<Recipe> replacement = findRecipe(wanted, recipes, profile, validator, meal.getRecipe());
        if (replacement.isEmpty()) {
            return new Outcome(null, "No suitable recipe matches \"" + wanted + "\" for your restrictions.");
        }
        PlannedMeal next = new PlannedMeal(meal.getDay(), meal.getMealType(), replacement.get(), meal.getServings());
        return new Outcome(new PlanEdit(EditOperation.REPLACE, meal, next), "Replace " + meal + " with "
            + replacement.get().getName());
    }

    /** Finds the planned meal named by day and/or meal type words. */
    static Optional<PlannedMeal> findTarget(String t, MealPlan plan) {
        DayOfWeek day = parseDay(t);
        MealType type = null;
        for (MealType mt : MealType.values()) {
            if (t.contains(mt.name().toLowerCase(Locale.ROOT))) {
                type = mt;
                break;
            }
        }
        if (type == null && t.contains("supper")) {
            type = MealType.DINNER;
        }
        List<PlannedMeal> candidates = new ArrayList<>();
        for (PlannedMeal meal : plan.getMeals()) {
            if ((day == null || meal.getDay() == day) && (type == null || meal.getMealType() == type)) {
                candidates.add(meal);
            }
        }
        if (day == null && type == null) {
            return Optional.empty();
        }
        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    static DayOfWeek parseDay(String t) {
        if (t.contains("today")) {
            return LocalDate.now().getDayOfWeek();
        }
        if (t.contains("tomorrow")) {
            return LocalDate.now().plusDays(1).getDayOfWeek();
        }
        for (DayOfWeek d : DayOfWeek.values()) {
            String full = d.name().toLowerCase(Locale.ROOT);
            if (Pattern.compile("\\b(" + full + "|" + full.substring(0, 3) + ")\\b").matcher(t).find()) {
                return d;
            }
        }
        return null;
    }

    /** Recipe by id, by name containment, or by keyword search, skipping unacceptable ones. */
    static Optional<Recipe> findRecipe(String wanted, RecipeService recipes, UserProfile profile,
                                       PlanValidator validator, Recipe current) {
        List<Recipe> options = new ArrayList<>();
        recipes.findByIdOrName(wanted).ifPresent(options::add);
        for (Recipe r : recipes.getAllRecipes()) {
            String name = r.getName().toLowerCase(Locale.ROOT);
            if (name.contains(wanted) || wanted.contains(name)) {
                options.add(r);
            }
        }
        options.addAll(recipes.search(wanted.replaceAll("\\b(something|a|an|the|recipe|meal|dish)\\b", " ")));
        for (Recipe r : options) {
            if (!r.equals(current) && validator.isAcceptable(r, profile)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
