package mealplanner;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import mealplanner.model.IngredientRequirement;
import mealplanner.model.Recipe;

/** Recipe model, Builder validation and F07 serving-size scaling. */
class RecipeTest {

    private Recipe chickenRice() {
        return Recipe.builder("Chicken Rice").id("cr")
            .ingredient("Chicken", 300, "g").ingredient("Rice", 150, "g").ingredient("Onion", 1, "pcs")
            .step("Cook the rice").step("Cook the chicken").step("Combine and serve")
            .cookingTimeMinutes(30).servings(2).estimatedCost(8).tag("Dairy-Free").build();
    }

    @Test
    void basicPropertiesAndCaseInsensitiveIngredients() {
        Recipe recipe = chickenRice();
        assertEquals("Chicken Rice", recipe.getName());
        assertEquals(30, recipe.getCookingTimeMinutes());
        assertEquals(2, recipe.getServings());
        assertTrue(recipe.containsIngredient("chicken"));
        assertFalse(recipe.containsIngredient("Beef"));
        assertTrue(recipe.hasTag("dairy-free"));
        assertEquals(4.0, recipe.getCostPerServing());
    }

    @Test
    void returnedCollectionsCannotModifyRecipe() {
        Recipe recipe = chickenRice();
        recipe.getIngredients().clear();
        assertEquals(3, recipe.getIngredients().size());
        assertThrows(UnsupportedOperationException.class, () -> recipe.getSteps().clear());
    }

    @Test
    void builderRejectsInvalidRecipes() {
        assertThrows(IllegalArgumentException.class, () -> Recipe.builder(" ").ingredient("x", 1, "g").build());
        assertThrows(IllegalArgumentException.class, () -> Recipe.builder("Empty").build());
        assertThrows(IllegalArgumentException.class,
            () -> Recipe.builder("Bad").ingredient("x", 1, "g").servings(0).build());
        assertThrows(IllegalArgumentException.class,
            () -> Recipe.builder("Bad").ingredient("x", 1, "g").estimatedCost(-1).build());
        assertThrows(IllegalArgumentException.class, () -> new IngredientRequirement("x", 0, "g"));
    }

    @Test
    void idIsDerivedFromNameWhenMissing() {
        assertEquals("mac-cheese", Recipe.builder("Mac & Cheese!").ingredient("pasta", 1, "g").build().getId());
    }

    @Test
    void scaleToRecalculatesQuantitiesAndCostWithoutChangingOriginal() {
        Recipe recipe = chickenRice();
        Recipe scaled = recipe.scaleTo(5);
        assertEquals(5, scaled.getServings());
        assertEquals(750, scaled.getIngredientRequirements().get(0).getQuantity(), 1e-9);
        assertEquals(2.5, scaled.getIngredientRequirements().get(2).getQuantity(), 1e-9);
        assertEquals(20.0, scaled.getEstimatedCost(), 1e-9);
        assertEquals(300, recipe.getIngredientRequirements().get(0).getQuantity(), "original unchanged");
        assertEquals(recipe.getId(), scaled.getId());
        assertThrows(IllegalArgumentException.class, () -> recipe.scaleTo(0));
        assertThrows(IllegalArgumentException.class, () -> recipe.scaleTo(-3));
    }

    @Test
    void describeListsIngredientsAndSteps() {
        String text = chickenRice().describe();
        assertTrue(text.contains("300 g Chicken"));
        assertTrue(text.contains("3. Combine and serve"));
    }
}
