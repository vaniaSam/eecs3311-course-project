
import java.util.List;

public class RecipeSearchTest {

    public static void main(String[] args) {

        RecipeSearch search = new RecipeSearch();

        Recipe chickenRice = new Recipe(
            "Chicken Rice",
            List.of("Chicken", "Rice"),
            List.of("Cook chicken", "Cook rice"),
            30, 2
        );

        Recipe vegetablePasta = new Recipe(
            "Vegetable Pasta",
            List.of("Pasta", "Tomato"),
            List.of("Boil pasta", "Prepare sauce"),
            20, 2
        );

        Recipe chickenSoup = new Recipe(
            "Chicken Soup",
            List.of("Chicken", "Carrot"),
            List.of("Boil ingredients"),
            45, 4
        );

        search.addRecipe(chickenRice);
        search.addRecipe(vegetablePasta);
        search.addRecipe(chickenSoup);

        if (search.size() != 3) {
            throw new AssertionError("Recipe count incorrect");
        }

        if (search.searchByName("chicken").size() != 2) {
            throw new AssertionError("Name search failed");
        }

        if (search.searchByIngredient("Chicken").size() != 2) {
            throw new AssertionError("Ingredient search failed");
        }

        if (search.filterByCookingTime(30).size() != 2) {
            throw new AssertionError("Cooking time filter failed");
        }

        if (!search.searchByName("unknown").isEmpty()) {
            throw new AssertionError("Unknown recipe search failed");
        }

        System.out.println("Recipe search test passed!");
    }
}
