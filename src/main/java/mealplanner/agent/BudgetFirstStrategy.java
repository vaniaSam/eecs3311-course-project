package mealplanner.agent;

import java.util.Locale;

import mealplanner.model.Pantry;
import mealplanner.model.PlanRequest;
import mealplanner.model.Quantities;
import mealplanner.model.Recipe;
import mealplanner.service.RecipeMatcher;

/**
 * <b>Strategy pattern - Concrete Strategy.</b> Prefers recipes with the lowest
 * estimated out-of-pocket cost: the recipe cost weighted by the share of
 * ingredients that are not already in the pantry, plus its cost per serving.
 */
public class BudgetFirstStrategy implements MealPlanningStrategy {

    private final RecipeMatcher matcher = new RecipeMatcher();

    @Override
    public String name() {
        return "Budget first";
    }

    @Override
    public double score(Recipe recipe, PlanRequest request, Pantry pantry) {
        return -outOfPocket(recipe, pantry) * 10 - recipe.getCostPerServing() * 4
            + MealPlanningStrategy.preferenceBonus(recipe, request);
    }

    @Override
    public String explain(Recipe recipe, PlanRequest request, Pantry pantry) {
        return String.format(Locale.ROOT, "About %s per serving; roughly %s of new groceries needed.",
            Quantities.money(recipe.getCostPerServing()), Quantities.money(outOfPocket(recipe, pantry)));
    }

    /** Approximate cost of the ingredients the pantry does not cover. */
    double outOfPocket(Recipe recipe, Pantry pantry) {
        double missingShare = 1 - matcher.getMatchPercentage(recipe, pantry) / 100.0;
        return recipe.getEstimatedCost() * missingShare;
    }
}
