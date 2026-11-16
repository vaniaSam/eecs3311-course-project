package mealplanner.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mealplanner.agent.PlanGenerationResult;
import mealplanner.agent.UnconfiguredLLMClient;
import mealplanner.command.CommandExecutor;
import mealplanner.command.RemoveMealCommand;
import mealplanner.command.ReplaceMealCommand;
import mealplanner.model.MealPlan;
import mealplanner.model.PantryItem;
import mealplanner.model.PlanRequest;
import mealplanner.model.PlannedMeal;
import mealplanner.model.PlannedMeal.MealType;
import mealplanner.model.Recipe;
import mealplanner.model.ShoppingList;
import mealplanner.service.PantryService;
import mealplanner.service.RecipeCatalogLoader;
import mealplanner.service.RecipeService;
import mealplanner.service.SampleData;
import mealplanner.service.ShoppingListService;

/** End-to-end behaviour through the controller and facade (deterministic paths, no LLM). */
class FacadeAndControllerTest {

    private MealPlanningFacade facade;
    private MealPlannerController controller;

    @BeforeEach
    void setUp() {
        facade = new MealPlanningFacade(SampleData.profile(), new RecipeService(RecipeCatalogLoader.loadDefault()),
            new PantryService(SampleData.pantry()), new ShoppingListService(), new UnconfiguredLLMClient());
        controller = new MealPlannerController(facade);
    }

    @Test
    void invalidProfileUpdateLeavesProfileUnchanged() {
        controller.saveProfile("50", "italian", "mushroom", "vegetarian");
        assertEquals(50, facade.getProfile().getWeeklyBudget());
        assertThrows(IllegalArgumentException.class, () -> controller.saveProfile("abc", "x", "", ""));
        assertThrows(IllegalArgumentException.class, () -> controller.saveProfile("-1", "x", "", ""));
        assertThrows(IllegalArgumentException.class, () -> controller.saveProfile("10", "", "", "vegan, carnivore"));
        assertEquals(50, facade.getProfile().getWeeklyBudget());
        assertEquals(Set.of("vegetarian"), facade.getProfile().getRestrictions());
        assertEquals(List.of("italian"), facade.getProfile().getPreferences());
    }

    @Test
    void pantryInputParsing() {
        PantryItem item = controller.managePantry("Tofu", "300", "g", "+4", "ingredient", "fridge");
        assertEquals(LocalDate.now().plusDays(4), item.getExpiryDate());
        assertEquals(PantryItem.ItemKind.LEFTOVER,
            controller.managePantry("soup", "1", "l", "2030-01-01", "leftover", "").getKind());
        assertNull(controller.managePantry("salt", "1", "kg", "-", "", "").getExpiryDate());
        assertThrows(IllegalArgumentException.class, () -> controller.managePantry("x", "0", "g", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> controller.managePantry("x", "1", "g", "tomorrow", "", ""));
        assertThrows(IllegalArgumentException.class, () -> controller.managePantry("x", "1", "g", "", "snack", ""));
        assertThrows(IllegalArgumentException.class, () -> controller.expiringWithin("-2"));
        assertEquals(1, controller.removePantryItem("tofu"));
    }

    @Test
    void requestValidation() {
        PlanRequest req = controller.buildRequest("budget", "3", "breakfast, dinner", "4", "cheap");
        assertEquals(6, req.slotCount());
        assertThrows(IllegalArgumentException.class, () -> controller.buildRequest("fancy", "3", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> controller.buildRequest("budget", "8", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> controller.buildRequest("budget", "3", "brunch", "", ""));
        assertThrows(IllegalArgumentException.class, () -> controller.buildRequest("budget", "3", "", "0", ""));
    }

    @Test
    void planShoppingListEditUndoFlow() {
        assertThrows(IllegalStateException.class, () -> controller.getShoppingList());
        assertFalse(controller.modifyPlan("remove monday dinner").applied());

        PlanGenerationResult result = controller.generatePlan(controller.buildRequest("pantry", "4", "dinner", "2", ""));
        assertTrue(result.accepted());
        MealPlan plan = controller.getCurrentPlan();
        assertSame(result.plan(), plan);
        ShoppingList before = controller.getShoppingList();
        int refreshes = facade.getShoppingListRefreshCount();

        PlannedMeal first = plan.getMeals().get(0);
        String instruction = "remove " + first.getDay().name().toLowerCase() + " dinner";
        PlanEditResult edit = controller.modifyPlan(instruction);
        assertTrue(edit.applied(), edit.message());
        assertEquals(3, plan.getScheduledMealCount());
        assertTrue(facade.getShoppingListRefreshCount() > refreshes, "observer recalculated the list");

        assertTrue(controller.undoLastEdit().applied());
        assertEquals(4, plan.getScheduledMealCount());
        assertEquals(before.describe(), controller.getShoppingList().describe());
        assertFalse(controller.undoLastEdit().applied(), "nothing left to undo");
    }

    @Test
    void manualReplaceIsValidatedAndUndoable() {
        controller.generatePlan(controller.buildRequest("pantry", "2", "dinner", "2", ""));
        PlannedMeal first = controller.getCurrentPlan().getMeals().get(0);
        controller.saveProfile("80", "", "", "vegetarian");
        PlanEditResult rejected = controller.replaceMeal(first.getDay().name(), "dinner", "r02");
        assertFalse(rejected.applied());
        assertTrue(rejected.message().contains("not vegetarian"));
        PlanEditResult ok = controller.replaceMeal(first.getDay().name(), "DINNER", "r11");
        assertTrue(ok.applied());
        assertEquals("r11", controller.getCurrentPlan().getMeal(first.getDay(), MealType.DINNER).getRecipe().getId());
        assertThrows(IllegalArgumentException.class, () -> controller.replaceMeal("funday", "dinner", "r11"));
        assertThrows(IllegalArgumentException.class, () -> controller.replaceMeal("monday", "dinner", "r404"));
    }

    @Test
    void rejectedPlanDoesNotReplaceCurrentPlan() {
        controller.generatePlan(controller.buildRequest("pantry", "2", "dinner", "2", ""));
        MealPlan accepted = controller.getCurrentPlan();
        controller.saveProfile("1", "", "", "");
        PlanGenerationResult rejected = controller.generatePlan(controller.buildRequest("pantry", "7", "dinner", "2",
            ""));
        assertFalse(rejected.accepted());
        assertSame(accepted, controller.getCurrentPlan());
    }

    @Test
    void scalingSearchAndSavingGeneratedRecipes() {
        assertEquals(6, controller.scaleRecipe("r04", "6").getServings());
        assertThrows(IllegalArgumentException.class, () -> controller.scaleRecipe("r04", "-1"));
        assertThrows(IllegalArgumentException.class, () -> controller.scaleRecipe("nope", "2"));
        assertThrows(IllegalArgumentException.class, () -> controller.searchRecipes(" "));
        assertThrows(IllegalArgumentException.class, () -> controller.searchByIngredients(","));
        assertFalse(controller.searchByPantry().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> controller.saveGeneratedRecipe(null));
        Recipe saved = controller.saveGeneratedRecipe(Recipe.builder("Test Bowl").ingredient("rice", 1, "g").build());
        assertEquals(saved, controller.getRecipe(saved.getId()));
    }

    @Test
    void aiOnlyFeaturesExplainMissingLLM() {
        assertFalse(controller.generateRecipe(controller.buildRequest("", "", "", "", "")).success());
        assertFalse(controller.askAssistant("hello").success());
        assertTrue(controller.llmStatus().startsWith("AI disabled"));
    }

    @Test
    void commandsExecuteAndUndo() {
        Recipe a = Recipe.builder("A").ingredient("x", 1, "g").build();
        Recipe b = Recipe.builder("B").ingredient("y", 1, "g").build();
        MealPlan plan = new MealPlan();
        PlannedMeal meal = new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, a);
        plan.addMeal(meal);
        CommandExecutor executor = new CommandExecutor();
        PlannedMeal next = new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, b);
        executor.execute(new ReplaceMealCommand(plan, meal, next));
        assertSame(next, plan.getMeal(DayOfWeek.MONDAY, MealType.DINNER));
        executor.execute(new RemoveMealCommand(plan, next));
        assertEquals(0, plan.getScheduledMealCount());
        assertEquals(2, executor.historyDescriptions().size());
        executor.undoLast();
        executor.undoLast();
        assertSame(meal, plan.getMeal(DayOfWeek.MONDAY, MealType.DINNER));
        assertFalse(executor.canUndo());
        assertThrows(IllegalArgumentException.class, () -> new ReplaceMealCommand(plan, meal,
            new PlannedMeal(DayOfWeek.TUESDAY, MealType.DINNER, b)));
        assertThrows(IllegalStateException.class, () -> executor.execute(new RemoveMealCommand(plan, next)));
        assertFalse(executor.canUndo(), "failed command is not recorded");
    }
}
