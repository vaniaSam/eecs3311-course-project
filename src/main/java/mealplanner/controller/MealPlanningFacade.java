package mealplanner.controller;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import mealplanner.agent.AssistantReply;
import mealplanner.agent.BudgetFirstStrategy;
import mealplanner.agent.EditInterpretation;
import mealplanner.agent.KitchenAssistant;
import mealplanner.agent.KitchenTools;
import mealplanner.agent.LLMClient;
import mealplanner.agent.LLMClientFactory;
import mealplanner.agent.LeftoverPlanResult;
import mealplanner.agent.MealPlanningAgent;
import mealplanner.agent.MealPlanningStrategy;
import mealplanner.agent.PantryFirstStrategy;
import mealplanner.agent.PlanGenerationResult;
import mealplanner.agent.PlanValidator;
import mealplanner.agent.RecipeGenerationResult;
import mealplanner.agent.RecommendationResult;
import mealplanner.agent.SubstitutionResult;
import mealplanner.command.CommandExecutor;
import mealplanner.command.MealPlanCommand;
import mealplanner.command.RemoveMealCommand;
import mealplanner.command.ReplaceMealCommand;
import mealplanner.config.AppConfig;
import mealplanner.model.EditOperation;
import mealplanner.model.MealPlan;
import mealplanner.model.Pantry;
import mealplanner.model.PantryItem;
import mealplanner.model.PlanEdit;
import mealplanner.model.PlanPriority;
import mealplanner.model.PlanRequest;
import mealplanner.model.PlannedMeal;
import mealplanner.model.PlannedMeal.MealType;
import mealplanner.model.Recipe;
import mealplanner.model.ShoppingList;
import mealplanner.model.UserProfile;
import mealplanner.model.ValidationResult;
import mealplanner.service.PantryService;
import mealplanner.service.RecipeCatalogLoader;
import mealplanner.service.RecipeMatch;
import mealplanner.service.RecipeService;
import mealplanner.service.SampleData;
import mealplanner.service.ShoppingListService;
import mealplanner.service.ShoppingListSynchronizer;

/**
 * <b>Facade pattern - Facade.</b> One simplified entry point to the
 * subsystem made of the agent, recipe/pantry/shopping services, the command
 * executor and the shopping-list observer. The controller (and therefore both
 * GUI and CLI) calls only this class, so they stay independent of how
 * features are orchestrated.
 *
 * <p>The facade also owns application state: the profile, the current meal
 * plan and the command history.</p>
 */
public class MealPlanningFacade {

    private final UserProfile profile;
    private final RecipeService recipeService;
    private final PantryService pantryService;
    private final ShoppingListService shoppingListService;
    private final ShoppingListSynchronizer synchronizer;
    private final MealPlanningAgent agent;
    private final CommandExecutor commands;
    private final KitchenAssistant assistant;
    private final Map<PlanPriority, MealPlanningStrategy> strategies = new EnumMap<>(PlanPriority.class);
    private MealPlan currentPlan;

    public MealPlanningFacade(UserProfile profile, RecipeService recipeService, PantryService pantryService,
                              ShoppingListService shoppingListService, LLMClient llm) {
        this.profile = Objects.requireNonNull(profile);
        this.recipeService = Objects.requireNonNull(recipeService);
        this.pantryService = Objects.requireNonNull(pantryService);
        this.shoppingListService = Objects.requireNonNull(shoppingListService);
        strategies.put(PlanPriority.BUDGET_FIRST, new BudgetFirstStrategy());
        strategies.put(PlanPriority.PANTRY_FIRST, new PantryFirstStrategy());
        PlanValidator validator = new PlanValidator();
        this.agent = new MealPlanningAgent(llm, recipeService, validator, strategies.get(PlanPriority.PANTRY_FIRST));
        this.commands = new CommandExecutor();
        this.synchronizer = new ShoppingListSynchronizer(pantryService, shoppingListService);
        this.assistant = new KitchenAssistant(llm, KitchenTools.create(recipeService, pantryService, validator,
            () -> this.profile, () -> currentPlan, () -> currentPlan == null ? null
                : synchronizer.getCurrentShoppingList()));
    }

    /** Wires the application with the seeded catalogue, sample pantry/profile and the configured LLM. */
    public static MealPlanningFacade createDefault(AppConfig config) {
        return new MealPlanningFacade(SampleData.profile(), new RecipeService(RecipeCatalogLoader.loadDefault()),
            new PantryService(SampleData.pantry()), new ShoppingListService(), LLMClientFactory.create(config));
    }

    // ---------------------------------------------------------------- F01

    /**
     * F01: copies validated values from {@code updated} into the stored
     * profile. All values are validated before any is applied, so a rejected
     * update leaves the profile unchanged.
     */
    public void saveProfile(UserProfile updated) {
        Objects.requireNonNull(updated, "Profile is required");
        UserProfile check = new UserProfile(updated.getWeeklyBudget());
        check.updatePreferences(updated.getPreferences());
        check.setDislikedIngredients(updated.getDislikedIngredients());
        check.setRestrictions(updated.getRestrictions());
        profile.setWeeklyBudget(check.getWeeklyBudget());
        profile.updatePreferences(check.getPreferences());
        profile.setDislikedIngredients(check.getDislikedIngredients());
        profile.setRestrictions(check.getRestrictions());
    }

    public UserProfile getProfile() {
        return profile;
    }

    // ---------------------------------------------------------------- F02

    /** F02: adds or merges a pantry item and refreshes the shopping list. */
    public PantryItem managePantry(PantryItem item) {
        PantryItem stored = pantryService.addOrUpdate(item);
        synchronizer.refresh();
        return stored;
    }

    /** F02: removes every entry for an ingredient. */
    public int removePantryItem(String name) {
        int removed = pantryService.remove(name);
        synchronizer.refresh();
        return removed;
    }

    public List<PantryItem> getPantryItems() {
        return pantryService.getPantry().getItems();
    }

    public List<PantryItem> expiringWithin(int days) {
        return pantryService.expiringWithin(days);
    }

    public List<PantryItem> expiredItems() {
        return pantryService.expired();
    }

    public List<PantryItem> findLeftovers() {
        return pantryService.findLeftovers();
    }

    // ---------------------------------------------------------------- F03

    /** F03: keyword search. */
    public List<Recipe> searchRecipes(String query) {
        return recipeService.search(query);
    }

    /** F03: search by typed ingredient names. */
    public List<RecipeMatch> searchByIngredients(List<String> ingredients) {
        return recipeService.findUsingNames(ingredients, pantryService.getPantry());
    }

    /** F03: search using all usable pantry items. */
    public List<RecipeMatch> searchByPantry() {
        Pantry pantry = pantryService.getPantry();
        List<PantryItem> usable = pantry.getItems().stream().filter(i -> !i.isExpired() && i.getAmount() > 0)
            .toList();
        return recipeService.findUsing(usable, pantry);
    }

    public List<Recipe> getAllRecipes() {
        return recipeService.getAllRecipes();
    }

    public Optional<Recipe> findRecipe(String idOrName) {
        return recipeService.findByIdOrName(idOrName);
    }

    // ---------------------------------------------------------------- F04 / F05

    /** F04: personalised recommendations using the strategy chosen in the request. */
    public RecommendationResult recommendRecipes(PlanRequest req, int limit) {
        useStrategyFor(req);
        return agent.recommend(req, pantryService.getPantry(), limit);
    }

    /** F05: generates (but does not save) a new recipe. */
    public RecipeGenerationResult generateRecipe(PlanRequest req) {
        return agent.generateRecipe(req, pantryService.getPantry());
    }

    /** F05: stores an accepted generated recipe in the recipe collection. */
    public Recipe saveGeneratedRecipe(Recipe recipe) {
        return recipeService.saveGenerated(recipe);
    }

    // ---------------------------------------------------------------- F06

    /**
     * F06: generates a plan; an accepted plan becomes the current plan,
     * the shopping-list observer is attached and the edit history is reset.
     * A rejected plan leaves the current plan unchanged.
     */
    public PlanGenerationResult generatePlan(PlanRequest req) {
        useStrategyFor(req);
        PlanGenerationResult result = agent.generate(req, pantryService.getPantry());
        if (result.accepted()) {
            currentPlan = result.plan();
            synchronizer.track(currentPlan);
            commands.clear();
        }
        return result;
    }

    public MealPlan getCurrentPlan() {
        return currentPlan;
    }

    // ---------------------------------------------------------------- F07 / F08 / F09

    /**
     * F07: scales a recipe without changing the stored version.
     *
     * @throws IllegalArgumentException for an unknown id or non-positive servings
     */
    public Recipe scaleRecipe(String recipeId, int servings) {
        Recipe recipe = requireRecipe(recipeId);
        return recipeService.scale(recipe, servings);
    }

    /** F08: substitutes for one ingredient of a recipe. */
    public SubstitutionResult suggestSubstitutions(String recipeId, String ingredient) {
        return agent.suggestSubstitutions(requireRecipe(recipeId), ingredient, profile, pantryService.getPantry());
    }

    /** F09: proposes a recipe that uses current leftovers. */
    public LeftoverPlanResult planFromLeftovers(PlanRequest req) {
        return agent.planFromLeftovers(pantryService.findLeftovers(), req, pantryService.getPantry());
    }

    // ---------------------------------------------------------------- F10

    /**
     * F10: the shopping list for the current plan, kept up to date by the
     * {@link ShoppingListSynchronizer} observer.
     *
     * @throws IllegalStateException if no plan exists
     */
    public ShoppingList getShoppingList() {
        if (currentPlan == null) {
            throw new IllegalStateException("There is no meal plan yet. Generate a plan first.");
        }
        return synchronizer.getCurrentShoppingList();
    }

    /** Stage 1 signature: builds a list for any plan. */
    public ShoppingList getShoppingList(MealPlan plan) {
        return shoppingListService.build(plan, pantryService.getPantry());
    }

    /** Number of observer-driven recalculations (shown in the UI to make the Observer visible). */
    public int getShoppingListRefreshCount() {
        return synchronizer.getRefreshCount();
    }

    // ---------------------------------------------------------------- F11

    /**
     * F11: interprets the instruction with the agent, converts the structured
     * edit into a command and executes it. The plan observer then refreshes
     * the shopping list. Rejected or ambiguous instructions change nothing.
     */
    public PlanEditResult modifyPlan(String text) {
        if (currentPlan == null) {
            return new PlanEditResult(false, "There is no meal plan yet. Generate a plan first.", null, null);
        }
        EditInterpretation interpretation = agent.interpretPlanEdit(currentPlan, text, profile);
        if (interpretation.edit() == null) {
            return new PlanEditResult(false, interpretation.message(), currentPlan, interpretation);
        }
        MealPlanCommand command = createEditCommand(interpretation.edit(), currentPlan);
        commands.execute(command);
        return new PlanEditResult(true, command.describe(), currentPlan, interpretation);
    }

    /** Chooses the concrete command for a structured edit. */
    public MealPlanCommand createEditCommand(PlanEdit edit, MealPlan plan) {
        if (edit.operation() == EditOperation.REMOVE) {
            return new RemoveMealCommand(plan, edit.targetMeal());
        }
        return new ReplaceMealCommand(plan, edit.targetMeal(), edit.replacementMeal());
    }

    /**
     * Deterministic manual replacement (GUI "Replace selected meal"), executed
     * through the same Command path so it is undoable.
     */
    public PlanEditResult replaceMeal(DayOfWeek day, MealType type, String recipeId) {
        if (currentPlan == null) {
            return new PlanEditResult(false, "There is no meal plan yet.", null, null);
        }
        PlannedMeal target = currentPlan.getMeal(day, type);
        if (target == null) {
            return new PlanEditResult(false, "Nothing is planned for " + day + " " + type + ".", currentPlan, null);
        }
        Recipe recipe = requireRecipe(recipeId);
        ValidationResult check = agent.getValidator().checkRecipe(recipe, profile);
        if (!check.isValid()) {
            return new PlanEditResult(false, String.join("; ", check.getErrors()), currentPlan, null);
        }
        MealPlanCommand command = createEditCommand(new PlanEdit(EditOperation.REPLACE, target,
            new PlannedMeal(day, type, recipe, target.getServings())), currentPlan);
        commands.execute(command);
        return new PlanEditResult(true, command.describe(), currentPlan, null);
    }

    /** Undoes the most recent plan edit. */
    public PlanEditResult undoLastEdit() {
        Optional<MealPlanCommand> undone = commands.undoLast();
        return undone.map(c -> new PlanEditResult(true, "Undid: " + c.describe(), currentPlan, null))
            .orElseGet(() -> new PlanEditResult(false, "Nothing to undo.", currentPlan, null));
    }

    public List<String> editHistory() {
        return commands.historyDescriptions();
    }

    // ---------------------------------------------------------------- F12

    /** F12: asks the tool-using kitchen assistant. */
    public AssistantReply askAssistant(String question) {
        return assistant.ask(question);
    }

    public void clearAssistantMemory() {
        assistant.clearMemory();
    }

    // ---------------------------------------------------------------- misc

    /** Description of the configured LLM (never contains the key). */
    public String llmStatus() {
        LLMClient llm = agent.getLlm();
        return (llm.isAvailable() ? "AI enabled: " : "AI disabled: ") + llm.describe()
            + (llm.getTotalTokens() > 0 ? " | tokens used this session: " + llm.getTotalTokens() : "");
    }

    public boolean isLlmAvailable() {
        return agent.getLlm().isAvailable();
    }

    public String currentStrategyName() {
        return agent.getStrategy().name();
    }

    private void useStrategyFor(PlanRequest req) {
        agent.setStrategy(strategies.get(req.getPriority()));
    }

    private Recipe requireRecipe(String idOrName) {
        return recipeService.findByIdOrName(idOrName)
            .orElseThrow(() -> new IllegalArgumentException("Unknown recipe: " + idOrName));
    }
}
