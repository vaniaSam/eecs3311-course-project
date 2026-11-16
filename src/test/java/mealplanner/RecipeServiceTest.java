package mealplanner;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import mealplanner.model.Pantry;
import mealplanner.model.PantryItem;
import mealplanner.model.Recipe;
import mealplanner.service.RecipeCatalogLoader;
import mealplanner.service.RecipeMatch;
import mealplanner.service.RecipeService;

/** F03 recipe search, F05 saving generated recipes, F07 scaling, and the seeded catalogue. */
class RecipeServiceTest {

    private final RecipeService service = new RecipeService(RecipeCatalogLoader.loadDefault());

    @Test
    void catalogueLoadsAndIdsAreUnique() {
        assertTrue(service.size() >= 20);
        assertTrue(service.getById("r03").isPresent());
        assertTrue(service.getById("R03").isPresent(), "lookup ignores case");
        assertTrue(service.getById("nope").isEmpty());
    }

    @Test
    void keywordSearchRequiresAllTermsAcrossNameIngredientsAndTags() {
        List<Recipe> pasta = service.search("pasta");
        assertTrue(pasta.stream().anyMatch(r -> r.getId().equals("r03")));
        List<Recipe> veganPasta = service.search("vegan pasta");
        assertTrue(veganPasta.stream().allMatch(r -> r.hasTag("vegan")));
        assertTrue(service.search("zzzz").isEmpty());
        assertTrue(service.search("  ").isEmpty());
        assertFalse(service.searchByName("salad").isEmpty());
        assertFalse(service.filterByCookingTime(10).isEmpty());
        assertTrue(service.filterByCookingTime(0).isEmpty());
    }

    @Test
    void ingredientSearchDistinguishesCookableFromPartialMatches() {
        Pantry pantry = new Pantry();
        for (String[] s : new String[][] {{"pasta", "500", "g"}, {"canned tomatoes", "800", "g"},
            {"garlic", "5", "pcs"}, {"olive oil", "10", "tbsp"}, {"onion", "3", "pcs"}}) {
            pantry.addItem(new PantryItem(s[0], Double.parseDouble(s[1]), s[2], LocalDate.now().plusDays(10),
                PantryItem.ItemKind.INGREDIENT, ""));
        }
        List<RecipeMatch> matches = service.findUsing(pantry.getItems(), pantry);
        assertFalse(matches.isEmpty());
        RecipeMatch first = matches.get(0);
        assertTrue(first.fullyCookable());
        assertEquals("r03", first.recipe().getId());
        assertTrue(matches.stream().anyMatch(m -> !m.fullyCookable() && !m.missingIngredients().isEmpty()));
        assertTrue(service.findUsingNames(List.of("unobtainium"), pantry).isEmpty());
    }

    @Test
    void saveGeneratedAssignsUniqueAiIds() {
        Recipe generated = Recipe.builder("Spaghetti Marinara").ingredient("pasta", 100, "g").build();
        Recipe first = service.saveGenerated(generated);
        Recipe second = service.saveGenerated(generated);
        assertNotEquals(first.getId(), second.getId());
        assertTrue(first.getId().startsWith("ai-"));
        assertEquals(Recipe.Source.AI_GENERATED, first.getSource());
        assertThrows(IllegalArgumentException.class, () -> service.addRecipe(service.getById("r01").get()));
    }

    @Test
    void scaleDelegatesToRecipe() {
        Recipe r = service.getById("r04").get();
        assertEquals(8, service.scale(r, 8).getServings());
        assertThrows(IllegalArgumentException.class, () -> service.scale(r, 0));
    }

    @Test
    void malformedCatalogueFailsClearly() {
        assertThrows(IllegalStateException.class, () -> RecipeCatalogLoader.load(new StringReader("{not json")));
        assertThrows(IllegalStateException.class, () -> RecipeCatalogLoader.load(
            new StringReader("{\"recipes\":[{\"name\":\"x\",\"servings\":2,\"cookingTimeMinutes\":5}]}")));
    }
}
