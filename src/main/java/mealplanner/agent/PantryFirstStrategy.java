package mealplanner.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import mealplanner.model.Pantry;
import mealplanner.model.PantryItem;
import mealplanner.model.PlanRequest;
import mealplanner.model.Recipe;
import mealplanner.service.RecipeMatcher;

/**
 * <b>Strategy pattern - Concrete Strategy.</b> Prefers recipes that use what
 * is already in the pantry, especially ingredients that expire within
 * {@value #EXPIRY_WINDOW_DAYS} days, to reduce food waste.
 *
 * <p>Evolved from the earlier {@code RecipeRecommender} class, whose
 * match-percentage ranking is now one term of this strategy's score.</p>
 */
public class PantryFirstStrategy implements MealPlanningStrategy {

    static final int EXPIRY_WINDOW_DAYS = 3;
    private final RecipeMatcher matcher = new RecipeMatcher();

    @Override
    public String name() {
        return "Pantry first";
    }

    @Override
    public double score(Recipe recipe, PlanRequest request, Pantry pantry) {
        double score = matcher.getMatchPercentage(recipe, pantry);
        score += 15 * expiringUsed(recipe, pantry).size();
        if (matcher.isFullyCookable(recipe, pantry)) {
            score += 10;
        }
        score += MealPlanningStrategy.preferenceBonus(recipe, request);
        score -= recipe.getCostPerServing() * 0.5; // tie-breaker only
        return score;
    }

    @Override
    public String explain(Recipe recipe, PlanRequest request, Pantry pantry) {
        List<String> expiring = expiringUsed(recipe, pantry);
        String text = String.format(Locale.ROOT, "Uses %.0f%% pantry ingredients", matcher.getMatchPercentage(recipe,
            pantry));
        if (!expiring.isEmpty()) {
            text += "; uses soon-expiring " + String.join(", ", expiring);
        }
        if (matcher.isFullyCookable(recipe, pantry)) {
            text += "; cookable now";
        }
        return text + ".";
    }

    /** Recipe ingredients that are in the pantry and expire within the window. */
    static List<String> expiringUsed(Recipe recipe, Pantry pantry) {
        List<String> result = new ArrayList<>();
        for (PantryItem item : pantry.getExpiringSoonItems(EXPIRY_WINDOW_DAYS)) {
            if (recipe.containsIngredient(item.getIngredientName())
                    && result.stream().noneMatch(n -> n.equalsIgnoreCase(item.getIngredientName()))) {
                result.add(item.getIngredientName());
            }
        }
        return result;
    }
}
