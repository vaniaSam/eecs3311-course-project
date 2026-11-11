
import java.time.LocalDate;
import java.util.List;

public class RecipeRecommenderTest {

    public static void main(String[] args) {

        Pantry pantry = new Pantry();

        pantry.addItem(new PantryItem(
            "Rice", 1.0, "kg",
            LocalDate.now().plusDays(30),
            PantryItem.ItemKind.INGREDIENT,
            "Cupboard"
        ));

        pantry.addItem(new PantryItem(
            "Chicken", 1.0, "kg",
            LocalDate.now().plusDays(3),
            PantryItem.ItemKind.INGREDIENT,
            "Fridge"
        ));

        Recipe fullMatch = new Recipe(
            "Chicken Rice",
            List.of("Chicken", "Rice"),
            List.of("Cook and serve"),
            30, 2
        );

        Recipe partialMatch = new Recipe(
            "Chicken Soup",
            List.of("Chicken", "Carrot"),
            List.of("Boil and serve"),
            40, 2
        );

        Recipe noMatch = new Recipe(
            "Tomato Pasta",
            List.of("Tomato", "Pasta"),
            List.of("Cook and serve"),
            20, 2
        );

        RecipeRecommender recommender = new RecipeRecommender();

        List<Recipe> ranked = recommender.recommendRecipes(
            List.of(noMatch, partialMatch, fullMatch),
            pantry
        );

        if (ranked.size() != 3
                || ranked.get(0) != fullMatch
                || ranked.get(1) != partialMatch
                || ranked.get(2) != noMatch) {
            throw new AssertionError("Recipe ranking failed");
        }

        List<Recipe> recommended =
            recommender.recommendAboveThreshold(
                ranked, pantry, 50.0
            );

        if (recommended.size() != 2
                || !recommended.contains(fullMatch)
                || !recommended.contains(partialMatch)) {
            throw new AssertionError("Recommendation threshold failed");
        }

        System.out.println("Recipe recommender test passed!");
    }
}
