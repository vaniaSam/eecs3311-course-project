
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Recommends recipes based on available pantry ingredients.
 * Recipes with higher ingredient match percentages appear first.
 */
public class RecipeRecommender {

    private final RecipeMatcher matcher = new RecipeMatcher();

    public List<Recipe> recommendRecipes(
            List<Recipe> recipes, Pantry pantry) {

        if (recipes == null || pantry == null) {
            throw new IllegalArgumentException(
                "Recipes and pantry cannot be null"
            );
        }

        List<Recipe> ranked = new ArrayList<>(recipes);

        ranked.sort(
            Comparator.comparingDouble(
                (Recipe recipe) ->
                    matcher.getMatchPercentage(recipe, pantry)
            ).reversed()
        );

        return ranked;
    }

    /**
     * Returns only recipes meeting the minimum pantry match percentage.
     */
    public List<Recipe> recommendAboveThreshold(
            List<Recipe> recipes,
            Pantry pantry,
            double minimumPercentage) {

        if (minimumPercentage < 0 || minimumPercentage > 100
                || Double.isNaN(minimumPercentage)) {
            throw new IllegalArgumentException(
                "Minimum percentage must be between 0 and 100"
            );
        }

        List<Recipe> ranked = recommendRecipes(recipes, pantry);
        List<Recipe> results = new ArrayList<>();

        for (Recipe recipe : ranked) {
            if (matcher.getMatchPercentage(recipe, pantry)
                    >= minimumPercentage) {
                results.add(recipe);
            }
        }

        return results;
    }
}
