
import java.time.LocalDate;
import java.util.List;

public class RecipeMatcherTest {

    public static void main(String[] args) {

        Recipe recipe = new Recipe(
            "Chicken Rice",
            List.of("Chicken", "Rice", "Onion"),
            List.of("Cook and combine ingredients"),
            30,
            2
        );

        Pantry pantry = new Pantry();

        pantry.addItem(new PantryItem(
            "Chicken", 2.0, "kg",
            LocalDate.now().plusDays(3),
            PantryItem.ItemKind.INGREDIENT,
            "Fridge"
        ));

        pantry.addItem(new PantryItem(
            "Rice", 1.0, "kg",
            LocalDate.now().plusDays(30),
            PantryItem.ItemKind.INGREDIENT,
            "Cupboard"
        ));

        RecipeMatcher matcher = new RecipeMatcher();

        // Onion is missing.
        List<String> missing =
            matcher.getMissingIngredients(recipe, pantry);

        if (missing.size() != 1 || !missing.contains("Onion")) {
            throw new AssertionError("Missing ingredient detection failed");
        }

        // Two of three ingredients are available.
        double percentage =
            matcher.getMatchPercentage(recipe, pantry);

        if (Math.abs(percentage - 66.6667) > 0.01) {
            throw new AssertionError("Match percentage incorrect");
        }

        // Adding expired onion should not make it available.
        pantry.addItem(new PantryItem(
            "Onion", 1.0, "kg",
            LocalDate.now().minusDays(1),
            PantryItem.ItemKind.INGREDIENT,
            "Fridge"
        ));

        if (!matcher.getMissingIngredients(recipe, pantry)
                .contains("Onion")) {
            throw new AssertionError("Expired ingredient was counted");
        }

        // Adding fresh onion should complete the recipe.
        pantry.addItem(new PantryItem(
            "Onion", 1.0, "kg",
            LocalDate.now().plusDays(7),
            PantryItem.ItemKind.INGREDIENT,
            "Cupboard"
        ));

        if (matcher.getMatchPercentage(recipe, pantry) != 100.0) {
            throw new AssertionError("Full pantry match failed");
        }

        System.out.println("Recipe matcher test passed!");
    }
}

