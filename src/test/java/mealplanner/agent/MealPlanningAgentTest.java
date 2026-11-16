package mealplanner.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import mealplanner.model.EditOperation;
import mealplanner.model.MealPlan;
import mealplanner.model.Pantry;
import mealplanner.model.PantryItem;
import mealplanner.model.PlanPriority;
import mealplanner.model.PlanRequest;
import mealplanner.model.PlannedMeal;
import mealplanner.model.PlannedMeal.MealType;
import mealplanner.model.Recipe;
import mealplanner.model.UserProfile;
import mealplanner.service.RecipeCatalogLoader;
import mealplanner.service.RecipeService;
import mealplanner.service.SampleData;

/**
 * Agent behaviour with a scripted LLM: grounding, validation, revision loops,
 * hallucination handling and deterministic fallbacks.
 */
class MealPlanningAgentTest {

    private static final LocalDate WEEK_START = LocalDate.of(2026, 11, 16);
    private final RecipeService recipes = new RecipeService(RecipeCatalogLoader.loadDefault());
    private final Pantry pantry = SampleData.pantry();
    private final UserProfile profile = new UserProfile(80);

    private MealPlanningAgent agent(LLMClient llm) {
        return new MealPlanningAgent(llm, recipes, new PlanValidator(), new PantryFirstStrategy());
    }

    private PlanRequest request(int days, String instruction) {
        return new PlanRequest(profile, WEEK_START, PlanPriority.PANTRY_FIRST, instruction, days,
            EnumSet.of(MealType.DINNER), 2);
    }

    /** Candidate ids listed in the prompt, in order. */
    private static List<String> candidateIds(String prompt) {
        List<String> ids = new ArrayList<>();
        Matcher m = Pattern.compile("(?m)^(r\\d\\d) \\|").matcher(prompt);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    private static String planJson(List<String[]> meals) {
        StringBuilder sb = new StringBuilder("{\"meals\":[");
        for (int i = 0; i < meals.size(); i++) {
            String[] m = meals.get(i);
            sb.append(i > 0 ? "," : "").append("{\"day\":\"").append(m[0]).append("\",\"mealType\":\"DINNER\","
                + "\"recipeId\":\"").append(m[1]).append("\"}");
        }
        return sb.append("],\"rationale\":\"Uses pantry staples.\"}").toString();
    }

    // ---------------------------------------------------------------- F06

    @Test
    void planIsRevisedAfterValidationFeedbackThenAccepted() {
        DayOfWeek d1 = WEEK_START.getDayOfWeek();
        DayOfWeek d2 = d1.plus(1);
        ScriptedLLMClient llm = new ScriptedLLMClient()
            .reply(planJson(List.<String[]>of(new String[] {d1.name(), "made-up"})))
            .compute(convo -> {
                List<String> ids = candidateIds(convo.get(1).content());
                return "```json\n" + planJson(List.<String[]>of(new String[] {d1.name(), ids.get(0)},
                    new String[] {d2.name(), ids.get(1)})) + "\n```";
            });
        PlanGenerationResult result = agent(llm).generate(request(2, "something warm"), pantry);

        assertTrue(result.accepted(), result.validation().toString());
        assertTrue(result.aiUsed());
        assertEquals(2, result.plan().getScheduledMealCount());
        assertEquals("Uses pantry staples.", result.rationale());
        assertEquals(2, llm.calls.size());
        String feedback = llm.lastUserMessage();
        assertTrue(feedback.contains("Unknown recipeId 'made-up'"), feedback);
        assertTrue(feedback.contains("Missing meal for " + d2), feedback);
        assertTrue(llm.calls.get(0).get(1).content().contains("User instruction: something warm"));
        assertTrue(result.trace().describe().contains("rejected"));
    }

    @Test
    void planFallsBackToStrategyWhenLLMNeverSucceeds() {
        ScriptedLLMClient llm = new ScriptedLLMClient().reply("not json").reply("{\"meals\":[]}").fail("timeout");
        PlanGenerationResult result = agent(llm).generate(request(7, ""), pantry);
        assertFalse(result.aiUsed());
        assertTrue(result.accepted(), result.validation().toString());
        assertEquals(7, result.plan().getScheduledMealCount());
        assertTrue(result.rationale().contains("AI service failed"));
        assertEquals(3, llm.calls.size());
    }

    @Test
    void deterministicPlanWithoutLLMRespectsBudgetRepeatsAndRestrictions() {
        profile.setRestrictions(Set.of("vegetarian"));
        profile.setWeeklyBudget(60); // the cheapest valid vegetarian week costs about $48
        PlanRequest req = new PlanRequest(profile, WEEK_START, PlanPriority.BUDGET_FIRST, "", 7,
            EnumSet.of(MealType.BREAKFAST, MealType.DINNER), 2);
        MealPlanningAgent a = agent(new UnconfiguredLLMClient());
        a.setStrategy(new BudgetFirstStrategy());
        PlanGenerationResult result = a.generate(req, pantry);
        assertFalse(result.aiUsed());
        assertTrue(result.accepted(), result.validation().toString());
        assertEquals(14, result.plan().getScheduledMealCount());
        assertTrue(result.plan().getEstimatedCost() <= 60);
        for (PlannedMeal m : result.plan().getMeals()) {
            assertEquals(m.getMealType() == MealType.BREAKFAST, m.getRecipe().hasTag("breakfast"), m.toString());
        }
    }

    @Test
    void impossibleBudgetIsReportedNotHidden() {
        profile.setWeeklyBudget(1);
        PlanGenerationResult result = agent(new UnconfiguredLLMClient()).generate(request(7, ""), pantry);
        assertFalse(result.accepted());
        assertTrue(result.validation().getErrors().stream().anyMatch(e -> e.contains("exceeds the budget")));
    }

    @Test
    void invalidRequestIsRejectedBeforeCallingTheLLM() {
        ScriptedLLMClient llm = new ScriptedLLMClient();
        PlanGenerationResult result = agent(llm).generate(request(9, ""), pantry);
        assertNull(result.plan());
        assertTrue(llm.calls.isEmpty());
    }

    // ---------------------------------------------------------------- F04

    @Test
    void recommendationsDiscardHallucinatedIdsAndFillFromStrategy() {
        ScriptedLLMClient llm = new ScriptedLLMClient().compute(convo -> {
            String first = candidateIds(convo.get(1).content()).get(2);
            return "{\"recommendations\":[{\"recipeId\":\"r99\",\"reason\":\"invented\"},"
                + "{\"recipeId\":\"" + first + "\",\"reason\":\"Great with your spinach\"},"
                + "{\"recipeId\":\"" + first + "\",\"reason\":\"duplicate\"}]}";
        });
        RecommendationResult result = agent(llm).recommend(request(7, ""), pantry, 4);
        assertTrue(result.aiUsed());
        assertEquals(4, result.recommendations().size());
        assertEquals("Great with your spinach", result.recommendations().get(0).reason());
        assertTrue(result.recommendations().stream().noneMatch(r -> r.recipe().getId().equals("r99")));
        assertTrue(result.trace().describe().contains("discarded 2"));
    }

    @Test
    void recommendationsExcludeRestrictedRecipesAndWorkWithoutLLM() {
        profile.setRestrictions(Set.of("vegan"));
        RecommendationResult result = agent(new UnconfiguredLLMClient()).recommend(request(7, ""), pantry, 5);
        assertFalse(result.aiUsed());
        assertEquals(5, result.recommendations().size());
        PlanValidator v = new PlanValidator();
        result.recommendations().forEach(r -> assertTrue(v.isAcceptable(r.recipe(), profile), r.recipe().getName()));
        assertTrue(result.notes().stream().anyMatch(n -> n.contains("excluded")));
    }

    // ---------------------------------------------------------------- F05

    private static final String CHICKEN_RECIPE = "{\"name\":\"Chicken Soup\",\"servings\":2,\"cookingTimeMinutes\":40,"
        + "\"estimatedCost\":9,\"tags\":[],\"ingredients\":[{\"name\":\"chicken breast\",\"quantity\":300,\"unit\":\"g\"}],"
        + "\"steps\":[\"Simmer\"]}";
    private static final String VEG_RECIPE = "{\"name\":\"Spinach Rice\",\"servings\":2,\"cookingTimeMinutes\":20,"
        + "\"estimatedCost\":3.5,\"tags\":[\"vegetarian\"],\"ingredients\":[{\"name\":\"rice\",\"quantity\":150,"
        + "\"unit\":\"g\"},{\"name\":\"spinach\",\"quantity\":100,\"unit\":\"g\"}],\"steps\":[\"Cook rice\","
        + "\"Stir in spinach\"]}";

    @Test
    void generatedRecipeViolatingRestrictionIsRevised() {
        profile.setRestrictions(Set.of("vegetarian"));
        ScriptedLLMClient llm = new ScriptedLLMClient().reply(CHICKEN_RECIPE).reply(VEG_RECIPE);
        RecipeGenerationResult result = agent(llm).generateRecipe(request(1, "quick dinner"), pantry);
        assertTrue(result.success(), result.message());
        assertEquals("Spinach Rice", result.recipe().getName());
        assertEquals(Recipe.Source.AI_GENERATED, result.recipe().getSource());
        assertTrue(llm.lastUserMessage().contains("not vegetarian"));
        assertTrue(result.validation().getWarnings().stream().anyMatch(w -> w.contains("verify allergens")));
    }

    @Test
    void generationGivesUpAfterMaxAttemptsAndNeedsLLM() {
        profile.setRestrictions(Set.of("vegetarian"));
        ScriptedLLMClient llm = new ScriptedLLMClient().reply(CHICKEN_RECIPE).reply("{\"name\":\"\"}")
            .reply(CHICKEN_RECIPE);
        RecipeGenerationResult result = agent(llm).generateRecipe(request(1, ""), pantry);
        assertFalse(result.success());
        assertTrue(result.message().contains("No acceptable recipe after 3 attempts"));

        RecipeGenerationResult offline = agent(new UnconfiguredLLMClient()).generateRecipe(request(1, ""), pantry);
        assertFalse(offline.success());
        assertTrue(offline.message().contains("needs an LLM"));
    }

    @Test
    void missingCostIsFlaggedNotInvented() {
        List<String> warnings = new ArrayList<>();
        Recipe r = agent(new ScriptedLLMClient()).parseRecipe(JsonExtractor.extractObject(
            "{\"name\":\"Toast\",\"ingredients\":[{\"name\":\"bread\",\"quantity\":2,\"unit\":\"slices\"}],"
                + "\"steps\":[\"Toast it\"]}"), warnings);
        assertEquals(0.0, r.getEstimatedCost());
        assertEquals(1, warnings.size());
    }

    // ---------------------------------------------------------------- F08

    @Test
    void substitutionsAreCheckedAgainstRestrictionsAndPantry() {
        profile.setRestrictions(Set.of("vegan"));
        Recipe omelette = recipes.getById("r05").orElseThrow();
        ScriptedLLMClient llm = new ScriptedLLMClient().reply(
            "{\"substitutes\":[{\"name\":\"butter\",\"note\":\"bad idea\"},{\"name\":\"oat milk\",\"note\":\"1:1\"}]}");
        SubstitutionResult result = agent(llm).suggestSubstitutions(omelette, "milk", profile, pantry);
        assertTrue(result.aiUsed());
        Substitution butter = result.substitutions().stream().filter(s -> s.name().equals("butter")).findFirst()
            .orElseThrow();
        assertFalse(butter.acceptable());
        assertTrue(butter.problem().contains("vegan"));
        assertTrue(result.substitutions().get(0).acceptable(), "acceptable ones first");
        assertTrue(llm.calls.get(0).get(1).content().contains("REFERENCE SUBSTITUTES"), "curated grounding sent");
        assertTrue(result.message().contains("not guaranteed"));
    }

    @Test
    void substitutionsFallBackToCatalogueAndValidateIngredient() {
        Recipe omelette = recipes.getById("r05").orElseThrow();
        SubstitutionResult offline = agent(new UnconfiguredLLMClient()).suggestSubstitutions(omelette, "egg",
            profile, pantry);
        assertFalse(offline.aiUsed());
        assertFalse(offline.substitutions().isEmpty());
        assertThrows(IllegalArgumentException.class,
            () -> agent(new UnconfiguredLLMClient()).suggestSubstitutions(omelette, "beef", profile, pantry));
    }

    // ---------------------------------------------------------------- F09

    @Test
    void leftoversCanPickCatalogueRecipeOrGenerateNewOne() {
        List<PantryItem> leftovers = new mealplanner.service.PantryService(pantry).findLeftovers();
        ScriptedLLMClient pick = new ScriptedLLMClient().reply("{\"choice\":\"r15\",\"reason\":\"uses the chicken\"}");
        LeftoverPlanResult chosen = agent(pick).planFromLeftovers(leftovers, request(1, ""), pantry);
        assertEquals("r15", chosen.recipe().getId());
        assertTrue(chosen.aiUsed());
        assertFalse(chosen.generated());

        ScriptedLLMClient generate = new ScriptedLLMClient().reply("{\"choice\":\"NEW\",\"reason\":\"fried rice\"}")
            .reply(VEG_RECIPE.replace("\"rice\"", "\"cooked rice\""));
        LeftoverPlanResult created = agent(generate).planFromLeftovers(leftovers, request(1, ""), pantry);
        assertTrue(created.generated());
        assertEquals(List.of("cooked rice"), created.leftoversUsed());
        assertTrue(generate.lastUserMessage().contains("Use up these leftovers"));
    }

    @Test
    void leftoversWithoutLLMOrWithoutLeftovers() {
        List<PantryItem> leftovers = new mealplanner.service.PantryService(pantry).findLeftovers();
        LeftoverPlanResult offline = agent(new UnconfiguredLLMClient()).planFromLeftovers(leftovers, request(1, ""),
            pantry);
        assertNotNull(offline.recipe());
        assertFalse(offline.aiUsed());
        PantryItem expired = new PantryItem("soup", 1, "l", LocalDate.now().minusDays(3),
            PantryItem.ItemKind.LEFTOVER, "");
        LeftoverPlanResult none = agent(new UnconfiguredLLMClient()).planFromLeftovers(List.of(expired),
            request(1, ""), pantry);
        assertNull(none.recipe());
        assertTrue(none.reason().contains("no usable leftovers"));
    }

    // ---------------------------------------------------------------- F11

    private MealPlan twoDayPlan() {
        MealPlan plan = new MealPlan(WEEK_START);
        plan.addMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, recipes.getById("r02").orElseThrow(), 2));
        plan.addMeal(new PlannedMeal(DayOfWeek.TUESDAY, MealType.DINNER, recipes.getById("r03").orElseThrow(), 2));
        return plan;
    }

    @Test
    void llmEditIsVerifiedAndRevised() {
        ScriptedLLMClient llm = new ScriptedLLMClient()
            .reply("{\"operation\":\"REPLACE\",\"day\":\"MONDAY\",\"mealType\":\"DINNER\","
                + "\"replacementRecipeId\":\"r77\"}")
            .reply("{\"operation\":\"REPLACE\",\"day\":\"MONDAY\",\"mealType\":\"DINNER\","
                + "\"replacementRecipeId\":\"r09\",\"explanation\":\"Swapped to a vegetarian curry\"}");
        MealPlan plan = twoDayPlan();
        EditInterpretation edit = agent(llm).interpretPlanEdit(plan, "make monday vegetarian", profile);
        assertNotNull(edit.edit());
        assertEquals(EditOperation.REPLACE, edit.edit().operation());
        assertEquals("r09", edit.edit().replacementMeal().getRecipe().getId());
        assertTrue(llm.lastUserMessage().contains("unknown replacementRecipeId 'r77'"));
        assertEquals("r02", plan.getMeal(DayOfWeek.MONDAY, MealType.DINNER).getRecipe().getId(),
            "interpretation never mutates the plan");
    }

    @Test
    void unsupportedOrAmbiguousEditsChangeNothing() {
        ScriptedLLMClient llm = new ScriptedLLMClient().reply(
            "{\"operation\":\"UNSUPPORTED\",\"explanation\":\"Which day do you mean?\"}");
        EditInterpretation edit = agent(llm).interpretPlanEdit(twoDayPlan(), "change dinner", profile);
        assertNull(edit.edit());
        assertEquals("Which day do you mean?", edit.message());
        assertNull(agent(llm).interpretPlanEdit(new MealPlan(), "remove monday", profile).edit());
    }

    @Test
    void restrictedReplacementIsRejected() {
        profile.setRestrictions(Set.of("vegetarian"));
        ScriptedLLMClient llm = new ScriptedLLMClient()
            .reply("{\"operation\":\"REPLACE\",\"day\":\"TUESDAY\",\"mealType\":\"DINNER\",\"replacementRecipeId\":\"r02\"}")
            .reply("{\"operation\":\"REPLACE\",\"day\":\"TUESDAY\",\"mealType\":\"DINNER\",\"replacementRecipeId\":\"r10\"}");
        EditInterpretation edit = agent(llm).interpretPlanEdit(twoDayPlan(), "chicken on tuesday", profile);
        assertNull(edit.edit());
        assertTrue(edit.trace().describe().contains("not vegetarian"));
    }

    @Test
    void ruleBasedParserIsUsedWithoutLLM() {
        MealPlanningAgent a = agent(new UnconfiguredLLMClient());
        MealPlan plan = twoDayPlan();
        EditInterpretation remove = a.interpretPlanEdit(plan, "Remove Tuesday dinner please", profile);
        assertEquals(EditOperation.REMOVE, remove.edit().operation());
        assertFalse(remove.aiUsed());
        EditInterpretation replace = a.interpretPlanEdit(plan, "replace mon dinner with greek salad", profile);
        assertEquals("r11", replace.edit().replacementMeal().getRecipe().getId());
        assertNull(a.interpretPlanEdit(plan, "make it nicer", profile).edit());
        assertNull(a.interpretPlanEdit(plan, "remove friday dinner", profile).edit());
    }
}
