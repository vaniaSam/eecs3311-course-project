
import java.time.LocalDate;
import java.util.List;

public class ShoppingListGeneratorTest {

    public static void main(String[] args) {

        Pantry pantry = new Pantry();

        pantry.addItem(new PantryItem(
            "Rice", 2.0, "kg",
            LocalDate.now().plusDays(30),
            PantryItem.ItemKind.INGREDIENT,
            "Cupboard"
        ));

        Recipe chickenRice = new Recipe(
            "Chicken Rice",
            List.of("Chicken", "Rice", "Onion"),
            List.of("Cook and serve"),
            30, 2
        );

        Recipe chickenSoup = new Recipe(
            "Chicken Soup",
            List.of("chicken", "Carrot", "Onion"),
            List.of("Boil and serve"),
            40, 2
        );

        ShoppingListGenerator generator =
            new ShoppingListGenerator();

        List<String> single =
            generator.generate(chickenRice, pantry);

        if (single.size() != 2
                || !single.contains("Chicken")
                || !single.contains("Onion")) {
            throw new AssertionError(
                "Single-recipe shopping list failed"
            );
        }

        List<String> combined = generator.generateForRecipes(
            List.of(chickenRice, chickenSoup), pantry
        );

        if (combined.size() != 3
                || !combined.contains("Chicken")
                || !combined.contains("Onion")
                || !combined.contains("Carrot")) {
            throw new AssertionError(
                "Combined shopping list or deduplication failed"
            );
        }

        System.out.println("Shopping list generator test passed!");
    }
}
