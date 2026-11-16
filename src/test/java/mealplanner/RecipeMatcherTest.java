package mealplanner;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import mealplanner.model.Pantry;
import mealplanner.model.PantryItem;
import mealplanner.model.Recipe;
import mealplanner.service.RecipeMatcher;

/** Pantry-aware matching used by F03, F04 and F06. */
class RecipeMatcherTest {

    private final Recipe recipe = Recipe.builder("Chicken Rice")
        .ingredient("Chicken", 0.5, "kg").ingredient("Rice", 0.3, "kg").ingredient("Onion", 1, "pcs")
        .step("Cook and combine ingredients").build();

    private static PantryItem item(String name, double amount, String unit, int days) {
        return new PantryItem(name, amount, unit, LocalDate.now().plusDays(days), PantryItem.ItemKind.INGREDIENT, "");
    }

    @Test
    void detectsMissingAndPercentageIgnoringExpiredStock() {
        Pantry pantry = new Pantry();
        pantry.addItem(item("Chicken", 2.0, "kg", 3));
        pantry.addItem(item("Rice", 1.0, "kg", 30));
        RecipeMatcher matcher = new RecipeMatcher();

        assertEquals(List.of("Onion"), matcher.getMissingIngredients(recipe, pantry));
        assertEquals(66.6667, matcher.getMatchPercentage(recipe, pantry), 0.01);

        pantry.addItem(item("Onion", 1, "pcs", -1));
        assertTrue(matcher.getMissingIngredients(recipe, pantry).contains("Onion"), "expired onion is not usable");

        pantry.addItem(item("Onion", 1, "pcs", 7));
        assertEquals(100.0, matcher.getMatchPercentage(recipe, pantry));
        assertTrue(matcher.isFullyCookable(recipe, pantry));
    }

    @Test
    void cookableRequiresQuantityInSameUnit() {
        Pantry pantry = new Pantry();
        pantry.addItem(item("Chicken", 0.2, "kg", 3));
        pantry.addItem(item("Rice", 300, "g", 30));
        pantry.addItem(item("Onion", 1, "pcs", 7));
        RecipeMatcher matcher = new RecipeMatcher();
        assertEquals(100.0, matcher.getMatchPercentage(recipe, pantry));
        assertFalse(matcher.isFullyCookable(recipe, pantry), "not enough chicken and rice is in grams");
    }

    @Test
    void nullArgumentsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeMatcher().getMissingIngredients(null, new Pantry()));
    }
}
