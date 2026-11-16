package mealplanner.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import mealplanner.model.Pantry;
import mealplanner.model.PlanRequest;
import mealplanner.model.Recipe;

/**
 * <b>Strategy pattern - Strategy interface.</b> Ranks candidate recipes
 * according to a planning priority. {@link MealPlanningAgent} (the Context)
 * holds one strategy and can switch it at run time with
 * {@code setStrategy}, so new priorities can be added without changing the
 * agent's recommendation and planning workflow.
 */
public interface MealPlanningStrategy {

    /** Display name, e.g. "Budget first". */
    String name();

    /** Higher is better. */
    double score(Recipe recipe, PlanRequest request, Pantry pantry);

    /** One-sentence deterministic explanation of why a recipe scored as it did. */
    String explain(Recipe recipe, PlanRequest request, Pantry pantry);

    /** Stage 1 operation: returns the recipes ordered best-first (input list is not modified). */
    default List<Recipe> rank(List<Recipe> recipes, PlanRequest request, Pantry pantry) {
        if (recipes == null || request == null || pantry == null) {
            throw new IllegalArgumentException("Recipes, request and pantry are required");
        }
        List<Recipe> ranked = new ArrayList<>(recipes);
        ranked.sort(Comparator.comparingDouble((Recipe r) -> score(r, request, pantry)).reversed()
            .thenComparing(Recipe::getId));
        return ranked;
    }

    /** Shared bonus for recipes that mention one of the user's stated preferences. */
    static double preferenceBonus(Recipe recipe, PlanRequest request) {
        String text = (recipe.getName() + " " + String.join(" ", recipe.getTags()) + " "
            + String.join(" ", recipe.getIngredients())).toLowerCase(Locale.ROOT);
        double bonus = 0;
        for (String pref : request.getProfile().getPreferences()) {
            for (String word : pref.toLowerCase(Locale.ROOT).split("\\s+")) {
                if (word.length() > 2 && text.contains(word.replaceAll("s$", ""))) {
                    bonus += 5;
                    break;
                }
            }
        }
        return bonus;
    }
}
