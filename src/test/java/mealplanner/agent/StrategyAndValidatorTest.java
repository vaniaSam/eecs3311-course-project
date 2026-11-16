package mealplanner.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import mealplanner.model.MealPlan;
import mealplanner.model.Pantry;
import mealplanner.model.PantryItem;
import mealplanner.model.PlanPriority;
import mealplanner.model.PlanRequest;
import mealplanner.model.PlannedMeal;
import mealplanner.model.PlannedMeal.MealType;
import mealplanner.model.Recipe;
import mealplanner.model.UserProfile;
import mealplanner.model.ValidationResult;

/** Strategy implementations, DietaryRules and PlanValidator. */
class StrategyAndValidatorTest {

    private final Recipe cheapPasta = Recipe.builder("Cheap Pasta").id("cheap").ingredient("pasta", 200, "g")
        .estimatedCost(2).servings(2).build();
    private final Recipe spinachEggs = Recipe.builder("Spinach Eggs").id("eggs").ingredient("spinach", 100, "g")
        .ingredient("egg", 2, "pcs").estimatedCost(6).servings(2).build();
    private final Recipe steak = Recipe.builder("Steak").id("steak").ingredient("beef steak", 400, "g")
        .estimatedCost(30).servings(2).build();

    private Pantry pantry() {
        Pantry p = new Pantry();
        p.addItem(new PantryItem("spinach", 200, "g", LocalDate.now().plusDays(1), PantryItem.ItemKind.INGREDIENT,
            ""));
        p.addItem(new PantryItem("egg", 6, "pcs", LocalDate.now().plusDays(10), PantryItem.ItemKind.INGREDIENT, ""));
        return p;
    }

    private PlanRequest request(UserProfile profile) {
        return PlanRequest.simple(profile, PlanPriority.PANTRY_FIRST, "");
    }

    @Test
    void pantryFirstPrefersRecipesUsingExpiringStock() {
        List<Recipe> ranked = new PantryFirstStrategy().rank(List.of(cheapPasta, steak, spinachEggs),
            request(new UserProfile(100)), pantry());
        assertEquals("eggs", ranked.get(0).getId());
        assertTrue(new PantryFirstStrategy().explain(spinachEggs, request(new UserProfile(100)), pantry())
            .contains("soon-expiring spinach"));
    }

    @Test
    void budgetFirstPrefersLowOutOfPocketCost() {
        List<Recipe> input = List.of(steak, spinachEggs, cheapPasta);
        List<Recipe> ranked = new BudgetFirstStrategy().rank(input, request(new UserProfile(100)), pantry());
        assertEquals("eggs", ranked.get(0).getId(), "eggs are free because they are in the pantry");
        assertEquals("steak", ranked.get(2).getId());
        assertEquals(List.of(steak, spinachEggs, cheapPasta), input, "input list is not modified");
    }

    @Test
    void preferencesAddBonus() {
        UserProfile p = new UserProfile(100);
        p.updatePreferences(List.of("pasta"));
        double with = new BudgetFirstStrategy().score(cheapPasta, request(p), new Pantry());
        double without = new BudgetFirstStrategy().score(cheapPasta, request(new UserProfile(100)), new Pantry());
        assertTrue(with > without);
    }

    @Test
    void dietaryRulesUseKeywordsWithExceptions() {
        assertTrue(DietaryRules.violates("vegetarian", "chicken breast"));
        assertTrue(DietaryRules.violates("Vegetarian", "Salmon"));
        assertFalse(DietaryRules.violates("vegetarian", "chickpeas"));
        assertTrue(DietaryRules.violates("dairy-free", "cheddar cheese"));
        assertFalse(DietaryRules.violates("dairy-free", "coconut milk"));
        assertFalse(DietaryRules.violates("lactose intolerant", "peanut butter"));
        assertTrue(DietaryRules.violates("nut allergy", "peanut butter"));
        assertFalse(DietaryRules.violates("nut-free", "coconut milk"));
        assertTrue(DietaryRules.violates("Gluten Free", "soy sauce"));
        assertFalse(DietaryRules.violates("egg-free", "eggplant"));
        assertTrue(DietaryRules.violates("vegan", "honey"));
        assertTrue(DietaryRules.violates("no mushrooms", "mushroom"));
        assertTrue(DietaryRules.violates("tomato allergy", "canned tomatoes"));
        assertFalse(DietaryRules.isKnown("no mushrooms"));
        assertTrue(DietaryRules.isKnown("celiac"));
    }

    @Test
    void checkRecipeReportsRestrictionsAndDislikes() {
        UserProfile p = new UserProfile(50);
        p.setRestrictions(Set.of("vegetarian", "no cilantro"));
        p.setDislikedIngredients(Set.of("spinach"));
        PlanValidator v = new PlanValidator();
        ValidationResult steakCheck = v.checkRecipe(steak, p);
        assertFalse(steakCheck.isValid());
        assertTrue(steakCheck.getErrors().get(0).contains("not vegetarian"));
        assertFalse(v.checkRecipe(spinachEggs, p).isValid(), "disliked spinach");
        ValidationResult pasta = v.checkRecipe(cheapPasta, p);
        assertTrue(pasta.isValid());
        assertTrue(pasta.getWarnings().stream().anyMatch(w -> w.contains("no cilantro")));
    }

    @Test
    void validatePlanChecksSlotsRepeatsAndBudget() {
        UserProfile p = new UserProfile(14); // $2 per day
        PlanRequest req = new PlanRequest(p, LocalDate.of(2026, 11, 16), PlanPriority.BUDGET_FIRST, "", 3,
            EnumSet.of(MealType.DINNER), 2);
        MealPlan plan = new MealPlan(req.getWeekStart());
        plan.addMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, cheapPasta, 2));
        plan.addMeal(new PlannedMeal(DayOfWeek.TUESDAY, MealType.DINNER, cheapPasta, 2));
        ValidationResult partial = new PlanValidator().validate(plan, req);
        assertTrue(partial.getErrors().stream().anyMatch(e -> e.contains("Missing meal for WEDNESDAY DINNER")));

        plan.addMeal(new PlannedMeal(DayOfWeek.WEDNESDAY, MealType.DINNER, cheapPasta, 2));
        ValidationResult full = new PlanValidator().validate(plan, req);
        assertTrue(full.getErrors().stream().anyMatch(e -> e.contains("used 3 times")));
        assertFalse(full.getErrors().stream().anyMatch(e -> e.contains("exceeds")), "$6 <= $6 budget");

        plan.replaceMeal(plan.getMeal(DayOfWeek.WEDNESDAY, MealType.DINNER),
            new PlannedMeal(DayOfWeek.WEDNESDAY, MealType.DINNER, steak, 2));
        assertTrue(new PlanValidator().validate(plan, req).getErrors().stream().anyMatch(e -> e.contains("exceeds")));
    }

    @Test
    void zeroBudgetMeansNoLimit() {
        PlanRequest req = new PlanRequest(new UserProfile(0), LocalDate.of(2026, 11, 16), PlanPriority.BUDGET_FIRST,
            "", 1, EnumSet.of(MealType.DINNER), 2);
        MealPlan plan = new MealPlan(req.getWeekStart());
        plan.addMeal(new PlannedMeal(DayOfWeek.MONDAY, MealType.DINNER, steak, 2));
        assertTrue(new PlanValidator().validate(plan, req).isValid());
    }
}
