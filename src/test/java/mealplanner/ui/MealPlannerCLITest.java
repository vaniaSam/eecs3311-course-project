package mealplanner.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import mealplanner.agent.UnconfiguredLLMClient;
import mealplanner.controller.MealPlannerController;
import mealplanner.controller.MealPlanningFacade;
import mealplanner.service.PantryService;
import mealplanner.service.RecipeCatalogLoader;
import mealplanner.service.RecipeService;
import mealplanner.service.SampleData;
import mealplanner.service.ShoppingListService;

/** Scripted CLI sessions exercising every command family. */
class MealPlannerCLITest {

    private String run(String script) {
        MealPlannerController controller = new MealPlannerController(new MealPlanningFacade(SampleData.profile(),
            new RecipeService(RecipeCatalogLoader.loadDefault()), new PantryService(SampleData.pantry()),
            new ShoppingListService(), new UnconfiguredLLMClient()));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new MealPlannerCLI(controller, new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
            new PrintStream(bytes, true, StandardCharsets.UTF_8), true).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void tokenizerKeepsQuotedGroups() {
        assertEquals(List.of("pantry", "add", "olive oil", "10", "tbsp"),
            MealPlannerCLI.tokenize("pantry add \"olive oil\"  10 tbsp"));
        assertEquals(List.of(), MealPlannerCLI.tokenize("   "));
    }

    @Test
    void fullScriptedSession() {
        String out = run(String.join("\n",
            "help", "profile restrictions vegetarian", "profile budget abc",
            "pantry add \"frozen peas\" 200 g +30 ingredient freezer", "pantry expiring 2", "pantry expired",
            "search curry", "search-ingredients egg", "search-pantry", "recipe r09", "recipes",
            "recommend --priority budget", "plan --days 3 --meals dinner", "show-plan", "shopping",
            "edit remove " + java.time.LocalDate.now().getDayOfWeek().name().toLowerCase() + " dinner",
            "history", "undo", "scale r09 4", "substitute r09 rice", "leftovers", "generate-recipe soup",
            "save-recipe", "ask hi", "forget", "trace off", "recommend", "plan --days", "nonsense", "exit",
            "status"));
        assertTrue(out.contains("Commands (quote multi-word values"));
        assertTrue(out.contains("Dietary restrictions: vegetarian"));
        assertTrue(out.contains("Error: Weekly budget must be a number"));
        assertTrue(out.contains("Pantry now has: 200 g frozen peas"));
        assertTrue(out.contains("[EXPIRED - not counted]"));
        assertTrue(out.contains("Chickpea Curry"));
        assertTrue(out.contains("Plan ACCEPTED"));
        assertTrue(out.contains("Shopping list:"));
        assertTrue(out.contains("Applied: Remove"));
        assertTrue(out.contains("Undid: Remove"));
        assertTrue(out.contains("Serves 4"));
        assertTrue(out.contains("Substitutes for rice"));
        assertTrue(out.contains("needs an LLM"));
        assertTrue(out.contains("Error: There is no generated recipe to save"));
        assertTrue(out.contains("Agent steps hidden."));
        assertTrue(out.contains("Error: Missing value for --days"));
        assertTrue(out.contains("Unknown command 'nonsense'"));
        assertTrue(out.contains("Goodbye."));
        assertFalse(out.contains("meal-planner> status\nAI"), "commands after exit are not run");
    }
}
