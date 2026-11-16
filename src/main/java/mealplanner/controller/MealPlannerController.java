package mealplanner.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import mealplanner.agent.AssistantReply;
import mealplanner.agent.LeftoverPlanResult;
import mealplanner.agent.PlanGenerationResult;
import mealplanner.agent.RecipeGenerationResult;
import mealplanner.agent.RecommendationResult;
import mealplanner.agent.SubstitutionResult;
import mealplanner.model.MealPlan;
import mealplanner.model.PantryItem;
import mealplanner.model.PlanPriority;
import mealplanner.model.PlanRequest;
import mealplanner.model.PlannedMeal.MealType;
import mealplanner.model.Recipe;
import mealplanner.model.ShoppingList;
import mealplanner.model.UserProfile;
import mealplanner.service.RecipeMatch;

/**
 * <b>MVC pattern - Controller.</b> Receives raw text input from either view
 * ({@code MealPlannerGUI} or {@code MealPlannerCLI}), validates and converts
 * it into domain objects, and delegates to {@link MealPlanningFacade}. Both
 * views therefore share exactly the same input rules and behaviour.
 *
 * <p>Invalid input raises {@link IllegalArgumentException} with a message
 * suitable for showing to the user.</p>
 */
public class MealPlannerController {

    private final MealPlanningFacade facade;

    public MealPlannerController(MealPlanningFacade facade) {
        this.facade = facade;
    }

    public MealPlanningFacade getFacade() {
        return facade;
    }

    // F01 -----------------------------------------------------------------

    /** F01: validates every field, then saves the profile atomically. */
    public UserProfile saveProfile(String budgetText, String preferencesCsv, String dislikesCsv,
                                   String restrictionsCsv) {
        UserProfile updated = new UserProfile(parseDouble(budgetText, "Weekly budget"));
        updated.updatePreferences(csv(preferencesCsv));
        updated.setDislikedIngredients(new LinkedHashSet<>(csv(dislikesCsv)));
        updated.setRestrictions(new LinkedHashSet<>(csv(restrictionsCsv)));
        facade.saveProfile(updated);
        return facade.getProfile();
    }

    public UserProfile getProfile() {
        return facade.getProfile();
    }

    // F02 -----------------------------------------------------------------

    /**
     * F02: adds or updates a pantry item.
     *
     * @param expiryText YYYY-MM-DD, "+N" for N days from today, or blank/"-"/"none" for unknown
     * @param kindText   "ingredient" (default) or "leftover"
     */
    public PantryItem managePantry(String name, String quantityText, String unit, String expiryText,
                                   String kindText, String note) {
        double quantity = parseDouble(quantityText, "Quantity");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        PantryItem.ItemKind kind = kindText == null || kindText.isBlank() ? PantryItem.ItemKind.INGREDIENT
            : parseKind(kindText);
        return facade.managePantry(new PantryItem(name, quantity, unit, parseExpiry(expiryText), kind, note));
    }

    public int removePantryItem(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ingredient name is required");
        }
        return facade.removePantryItem(name);
    }

    public List<PantryItem> getPantryItems() {
        return facade.getPantryItems();
    }

    public List<PantryItem> expiringWithin(String daysText) {
        int days = parseInt(daysText, "Days");
        if (days < 0) {
            throw new IllegalArgumentException("Days cannot be negative");
        }
        return facade.expiringWithin(days);
    }

    public List<PantryItem> expiredItems() {
        return facade.expiredItems();
    }

    public List<PantryItem> findLeftovers() {
        return facade.findLeftovers();
    }

    // F03 -----------------------------------------------------------------

    public List<Recipe> searchRecipes(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Enter at least one search term");
        }
        return facade.searchRecipes(query);
    }

    public List<RecipeMatch> searchByIngredients(String ingredientsCsv) {
        List<String> names = csv(ingredientsCsv);
        if (names.isEmpty()) {
            throw new IllegalArgumentException("Enter at least one ingredient");
        }
        return facade.searchByIngredients(names);
    }

    public List<RecipeMatch> searchByPantry() {
        return facade.searchByPantry();
    }

    public List<Recipe> getAllRecipes() {
        return facade.getAllRecipes();
    }

    public Recipe getRecipe(String idOrName) {
        return facade.findRecipe(idOrName).orElseThrow(
            () -> new IllegalArgumentException("Unknown recipe: " + idOrName));
    }

    // Requests ------------------------------------------------------------

    /**
     * Builds a validated {@link PlanRequest} from text fields.
     *
     * @param priorityText  "budget" or "pantry"
     * @param daysText      1-7
     * @param mealTypesCsv  e.g. "lunch, dinner" (blank = dinner)
     * @param servingsText  portions per meal
     * @param instruction   free text for the AI (may be blank)
     */
    public PlanRequest buildRequest(String priorityText, String daysText, String mealTypesCsv, String servingsText,
                                    String instruction) {
        PlanPriority priority = priorityText == null || priorityText.isBlank() ? PlanPriority.PANTRY_FIRST
            : PlanPriority.parse(priorityText);
        int days = daysText == null || daysText.isBlank() ? 7 : parseInt(daysText, "Days");
        int servings = servingsText == null || servingsText.isBlank() ? 2 : parseInt(servingsText, "Servings");
        Set<MealType> types = EnumSet.noneOf(MealType.class);
        for (String t : csv(mealTypesCsv)) {
            types.add(MealType.parse(t));
        }
        PlanRequest req = new PlanRequest(facade.getProfile(), LocalDate.now(), priority, instruction, days, types,
            servings);
        List<String> errors = req.validationErrors();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        return req;
    }

    // F04-F12 ---------------------------------------------------------------

    public RecommendationResult recommendRecipes(PlanRequest req) {
        return facade.recommendRecipes(req, 5);
    }

    public RecipeGenerationResult generateRecipe(PlanRequest req) {
        return facade.generateRecipe(req);
    }

    public Recipe saveGeneratedRecipe(Recipe recipe) {
        if (recipe == null) {
            throw new IllegalArgumentException("There is no generated recipe to save");
        }
        return facade.saveGeneratedRecipe(recipe);
    }

    public PlanGenerationResult generatePlan(PlanRequest req) {
        return facade.generatePlan(req);
    }

    public MealPlan getCurrentPlan() {
        return facade.getCurrentPlan();
    }

    public Recipe scaleRecipe(String recipeId, String servingsText) {
        int servings = parseInt(servingsText, "Servings");
        if (servings <= 0) {
            throw new IllegalArgumentException("Servings must be a positive whole number");
        }
        return facade.scaleRecipe(recipeId, servings);
    }

    public SubstitutionResult suggestSubstitutions(String recipeId, String ingredient) {
        return facade.suggestSubstitutions(recipeId, ingredient);
    }

    public LeftoverPlanResult planFromLeftovers(PlanRequest req) {
        return facade.planFromLeftovers(req);
    }

    public ShoppingList getShoppingList() {
        return facade.getShoppingList();
    }

    public PlanEditResult modifyPlan(String text) {
        return facade.modifyPlan(text);
    }

    public PlanEditResult replaceMeal(String dayText, String mealTypeText, String recipeId) {
        DayOfWeek day;
        try {
            day = DayOfWeek.valueOf(dayText.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown day: " + dayText);
        }
        return facade.replaceMeal(day, MealType.parse(mealTypeText), recipeId);
    }

    public PlanEditResult undoLastEdit() {
        return facade.undoLastEdit();
    }

    public AssistantReply askAssistant(String question) {
        return facade.askAssistant(question);
    }

    public void clearAssistantMemory() {
        facade.clearAssistantMemory();
    }

    public String llmStatus() {
        return facade.llmStatus();
    }

    // parsing helpers -------------------------------------------------------

    /** Splits comma-separated text into trimmed, non-blank values. */
    public static List<String> csv(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String part : text.split(",")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    static double parseDouble(String text, String field) {
        try {
            double v = Double.parseDouble(text == null ? "" : text.trim().replace("$", ""));
            if (!Double.isFinite(v)) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a number");
        }
    }

    static int parseInt(String text, String field) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a whole number");
        }
    }

    static LocalDate parseExpiry(String text) {
        if (text == null || text.isBlank() || text.trim().equals("-") || text.trim().equalsIgnoreCase("none")) {
            return null;
        }
        String t = text.trim();
        if (t.startsWith("+")) {
            return LocalDate.now().plusDays(parseInt(t.substring(1), "Days until expiry"));
        }
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Expiry must be YYYY-MM-DD, +N days, or blank");
        }
    }

    static PantryItem.ItemKind parseKind(String text) {
        try {
            return PantryItem.ItemKind.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Kind must be ingredient or leftover");
        }
    }
}
