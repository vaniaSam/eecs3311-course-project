package mealplanner.agent;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import mealplanner.model.EditOperation;
import mealplanner.model.MealPlan;
import mealplanner.model.Pantry;
import mealplanner.model.PantryItem;
import mealplanner.model.PlanEdit;
import mealplanner.model.PlanRequest;
import mealplanner.model.PlannedMeal;
import mealplanner.model.PlannedMeal.MealType;
import mealplanner.model.Quantities;
import mealplanner.model.Recipe;
import mealplanner.model.UserProfile;
import mealplanner.model.ValidationResult;
import mealplanner.service.RecipeMatch;
import mealplanner.service.RecipeMatcher;
import mealplanner.service.RecipeService;

/**
 * The AI planning agent (Stage 1 MealPlanningAgent).
 *
 * <p>The agent coordinates a multi-step process rather than forwarding a
 * prompt: it <em>retrieves</em> candidate recipes, filters them with
 * deterministic dietary checks, <em>ranks</em> them with the selected
 * {@link MealPlanningStrategy}, asks the LLM to <em>decide</em> or
 * <em>generate</em> using only that grounded context, <em>validates</em> the
 * proposal with {@link PlanValidator}, and feeds validation errors back to the
 * LLM for <em>revision</em>. Every step is recorded in an {@link AgentTrace}.
 * When the LLM is unavailable, hybrid features fall back to their
 * deterministic path and say so; AI-only features report the problem.</p>
 *
 * <p><b>Strategy pattern - Context role:</b> holds the current strategy and
 * exposes {@link #setStrategy(MealPlanningStrategy)}.</p>
 */
public class MealPlanningAgent {

    /** Maximum LLM proposals (initial + revisions) per request. */
    public static final int MAX_ATTEMPTS = 3;

    private static final String SYSTEM_PROMPT = "You are the planning component of a meal-planning application. "
        + "You only use recipes, ingredients and facts given to you. Reply with exactly one JSON object and no "
        + "other text.";

    private final LLMClient llm;
    private final RecipeService recipeService;
    private final PlanValidator validator;
    private final RecipeMatcher matcher = new RecipeMatcher();
    private MealPlanningStrategy strategy;

    public MealPlanningAgent(LLMClient llm, RecipeService recipeService, PlanValidator validator,
                             MealPlanningStrategy strategy) {
        this.llm = Objects.requireNonNull(llm, "LLM client is required");
        this.recipeService = Objects.requireNonNull(recipeService, "Recipe service is required");
        this.validator = Objects.requireNonNull(validator, "Validator is required");
        this.strategy = Objects.requireNonNull(strategy, "Strategy is required");
    }

    /** Switches the ranking strategy (Strategy pattern). */
    public void setStrategy(MealPlanningStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "Strategy is required");
    }

    public MealPlanningStrategy getStrategy() {
        return strategy;
    }

    public LLMClient getLlm() {
        return llm;
    }

    public PlanValidator getValidator() {
        return validator;
    }

    // ------------------------------------------------------------------ F04

    /**
     * F04: ranks recipes for the user's profile, pantry and priority. The
     * strategy produces a deterministic candidate ranking; the LLM then picks
     * and orders the best {@code limit} and explains each choice. Hallucinated
     * ids are discarded and gaps are filled from the deterministic ranking.
     */
    public RecommendationResult recommend(PlanRequest req, Pantry pantry, int limit) {
        AgentTrace trace = new AgentTrace();
        List<String> notes = new ArrayList<>();
        List<Recipe> eligible = eligibleRecipes(req.getProfile(), trace, notes);
        if (eligible.isEmpty()) {
            notes.add("No recipe satisfies your restrictions and dislikes.");
            return new RecommendationResult(List.of(), false, strategy.name(), notes, trace);
        }
        List<Recipe> ranked = strategy.rank(eligible, req, pantry);
        trace.add(strategy.name() + " strategy ranked " + ranked.size() + " candidates; top 3: "
            + names(ranked.subList(0, Math.min(3, ranked.size()))));
        List<Recipe> shortlist = ranked.subList(0, Math.min(Math.max(limit * 2, limit), ranked.size()));

        Map<String, Recommendation> deterministic = new LinkedHashMap<>();
        for (Recipe r : shortlist) {
            deterministic.put(r.getId(), new Recommendation(r, strategy.explain(r, req, pantry),
                strategy.score(r, req, pantry)));
        }

        if (llm.isAvailable()) {
            StringBuilder prompt = new StringBuilder();
            prompt.append("Recommend the best ").append(limit).append(" recipes for this user, best first.\n\n")
                .append("USER PROFILE\n").append(req.getProfile().describe()).append("\nPlanning priority: ")
                .append(strategy.name()).append('\n');
            appendInstruction(prompt, req);
            prompt.append("\nPANTRY (usable)\n").append(pantrySummary(pantry))
                .append("\nCANDIDATES (already filtered for restrictions, pre-ranked by the ")
                .append(strategy.name()).append(" strategy)\n");
            for (Recipe r : shortlist) {
                prompt.append(candidateLine(r, req, pantry)).append('\n');
            }
            prompt.append("\nOnly use recipeIds from CANDIDATES. Give one short, specific reason per recipe that "
                + "refers to the profile, pantry or budget. JSON format:\n"
                + "{\"recommendations\":[{\"recipeId\":\"r01\",\"reason\":\"...\"}]}");
            try {
                JsonObject json = JsonExtractor.extractObject(llm.complete(conversation(prompt.toString())));
                List<Recommendation> chosen = new ArrayList<>();
                int discarded = 0;
                for (JsonElement e : array(json, "recommendations")) {
                    JsonObject o = e.getAsJsonObject();
                    String id = JsonExtractor.string(o, "recipeId", "");
                    Recommendation base = deterministic.get(id);
                    if (base == null || chosen.stream().anyMatch(c -> c.recipe().getId().equals(id))) {
                        discarded++;
                        continue;
                    }
                    String reason = JsonExtractor.string(o, "reason", base.reason());
                    chosen.add(new Recommendation(base.recipe(), reason, base.score()));
                    if (chosen.size() == limit) {
                        break;
                    }
                }
                trace.add("LLM selected " + chosen.size() + " recipes" + (discarded > 0 ? "; discarded "
                    + discarded + " unknown/duplicate id(s)" : ""));
                for (Recommendation r : deterministic.values()) {
                    if (chosen.size() >= limit) {
                        break;
                    }
                    if (chosen.stream().noneMatch(c -> c.recipe().equals(r.recipe()))) {
                        chosen.add(r);
                        trace.add("Filled remaining slot with strategy pick " + r.recipe().getName());
                    }
                }
                return new RecommendationResult(chosen, true, strategy.name(), notes, trace);
            } catch (RuntimeException e) { // LLM failure or unusable model output
                trace.add("LLM step failed (" + e.getMessage() + "); using the strategy ranking only");
                notes.add("AI explanation unavailable: " + e.getMessage());
            }
        } else {
            trace.add("LLM not configured; using the strategy ranking only");
            notes.add("AI re-ranking is off because no LLM is configured; showing the " + strategy.name()
                + " ranking.");
        }
        List<Recommendation> fallback = new ArrayList<>(deterministic.values());
        return new RecommendationResult(fallback.subList(0, Math.min(limit, fallback.size())), false,
            strategy.name(), notes, trace);
    }

    // ------------------------------------------------------------------ F05

    /**
     * F05: asks the LLM for a new recipe in a strict JSON schema, converts it
     * into a {@link Recipe}, checks it with {@link PlanValidator#checkRecipe}
     * and requests revisions when it breaks a restriction. The recipe is not
     * saved here; the facade saves it when the user accepts it.
     */
    public RecipeGenerationResult generateRecipe(PlanRequest req, Pantry pantry) {
        return generateRecipe(req, pantry, req.getUserInstruction(), new AgentTrace());
    }

    private RecipeGenerationResult generateRecipe(PlanRequest req, Pantry pantry, String instruction,
                                                  AgentTrace trace) {
        if (!llm.isAvailable()) {
            trace.add("LLM not configured; recipe generation needs an LLM");
            return new RecipeGenerationResult(null, new ValidationResult(),
                "AI recipe generation needs an LLM. " + unavailableHint(), trace);
        }
        String prompt = "Create ONE new recipe.\n\nUSER PROFILE\n" + req.getProfile().describe()
            + "\nRequest: " + (instruction.isBlank() ? "a healthy everyday meal" : instruction)
            + "\nServings: " + req.getServingsPerMeal()
            + "\n\nPANTRY (prefer these, especially items expiring soon)\n" + pantrySummary(pantry)
            + "\n\nRules: respect every dietary restriction and never use disliked ingredients. Use metric or "
            + "'pcs'/'tbsp'/'tsp' units. estimatedCost is the approximate total cost in CAD for all servings. "
            + "JSON format:\n{\"name\":\"...\",\"servings\":2,\"cookingTimeMinutes\":30,\"estimatedCost\":8.5,"
            + "\"tags\":[\"vegetarian\"],\"ingredients\":[{\"name\":\"rice\",\"quantity\":150,\"unit\":\"g\"}],"
            + "\"steps\":[\"...\"]}";
        List<ChatMessage> convo = conversation(prompt);
        String lastProblem = "no attempt made";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String reply;
            try {
                reply = llm.complete(convo);
            } catch (LLMException e) {
                trace.add("Attempt " + attempt + ": LLM call failed - " + e.getMessage());
                return new RecipeGenerationResult(null, new ValidationResult(),
                    "The AI service failed: " + e.getMessage(), trace);
            }
            convo.add(ChatMessage.assistant(reply));
            Recipe recipe;
            List<String> warnings = new ArrayList<>();
            try {
                recipe = parseRecipe(JsonExtractor.extractObject(reply), warnings);
            } catch (RuntimeException e) { // malformed model output
                lastProblem = "malformed recipe: " + e.getMessage();
                trace.add("Attempt " + attempt + ": rejected " + lastProblem);
                convo.add(ChatMessage.user("That reply could not be used (" + e.getMessage()
                    + "). Reply again with the complete JSON recipe only."));
                continue;
            }
            ValidationResult check = validator.checkRecipe(recipe, req.getProfile());
            warnings.forEach(check::addWarning);
            if (check.isValid()) {
                trace.add("Attempt " + attempt + ": '" + recipe.getName() + "' passed validation");
                return new RecipeGenerationResult(recipe, check, "Generated '" + recipe.getName() + "'.", trace);
            }
            lastProblem = String.join("; ", check.getErrors());
            trace.add("Attempt " + attempt + ": '" + recipe.getName() + "' failed validation - " + lastProblem);
            convo.add(ChatMessage.user("The recipe breaks these rules: " + lastProblem
                + ". Revise it and reply with the complete corrected JSON only."));
        }
        return new RecipeGenerationResult(null, new ValidationResult(),
            "No acceptable recipe after " + MAX_ATTEMPTS + " attempts (last problem: " + lastProblem + ").", trace);
    }

    /** Converts the LLM's JSON recipe into a validated {@link Recipe}. Package-private for tests. */
    Recipe parseRecipe(JsonObject o, List<String> warnings) {
        String name = JsonExtractor.string(o, "name", "");
        Recipe.Builder b = Recipe.builder(name).source(Recipe.Source.AI_GENERATED);
        b.id("ai-" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", ""));
        b.servings(intValue(o, "servings", 2));
        b.cookingTimeMinutes(intValue(o, "cookingTimeMinutes", 30));
        if (o.has("estimatedCost") && o.get("estimatedCost").isJsonPrimitive()) {
            b.estimatedCost(o.get("estimatedCost").getAsDouble());
        } else {
            warnings.add("The AI did not estimate a cost; cost is shown as $0.00 (unknown)");
        }
        for (JsonElement e : array(o, "ingredients")) {
            JsonObject i = e.getAsJsonObject();
            double qty = i.has("quantity") && i.get("quantity").isJsonPrimitive()
                ? i.get("quantity").getAsDouble() : -1;
            b.ingredient(JsonExtractor.string(i, "name", ""), qty, JsonExtractor.string(i, "unit", ""));
        }
        for (JsonElement e : array(o, "steps")) {
            b.step(e.getAsString());
        }
        JsonElement tags = o.get("tags");
        if (tags != null && tags.isJsonArray()) { // tags are optional
            for (JsonElement e : tags.getAsJsonArray()) {
                b.tag(e.getAsString());
            }
        }
        Recipe recipe = b.build();
        if (recipe.getSteps().isEmpty()) {
            throw new IllegalArgumentException("recipe has no steps");
        }
        return recipe;
    }

    // ------------------------------------------------------------------ F06

    /**
     * F06: generates a weekly plan. The strategy ranks candidates, the LLM
     * assigns recipes to slots, the validator checks the proposal and the LLM
     * is asked to revise up to {@value #MAX_ATTEMPTS} times. If the LLM is
     * unavailable or never produces a valid plan, the plan is assembled
     * deterministically from the strategy ranking and labelled as such.
     */
    public PlanGenerationResult generate(PlanRequest req, Pantry pantry) {
        AgentTrace trace = new AgentTrace();
        List<String> requestErrors = req.validationErrors();
        if (!requestErrors.isEmpty()) {
            ValidationResult v = new ValidationResult();
            requestErrors.forEach(v::addError);
            trace.add("Invalid request: " + requestErrors);
            return new PlanGenerationResult(null, v, false, "The plan request is invalid.", trace);
        }
        List<String> notes = new ArrayList<>();
        List<Recipe> eligible = eligibleRecipes(req.getProfile(), trace, notes);
        if (eligible.isEmpty()) {
            ValidationResult v = new ValidationResult();
            v.addError("No recipe satisfies your restrictions and dislikes");
            return new PlanGenerationResult(null, v, false, "No recipes available.", trace);
        }
        List<Recipe> ranked = strategy.rank(eligible, req, pantry);
        trace.add(strategy.name() + " strategy ranked " + ranked.size() + " candidates; top 3: "
            + names(ranked.subList(0, Math.min(3, ranked.size()))));
        List<Recipe> shortlist = shortlist(ranked, req);
        Map<String, Recipe> byId = new HashMap<>();
        shortlist.forEach(r -> byId.put(r.getId(), r));

        String fallbackReason;
        if (llm.isAvailable()) {
            List<ChatMessage> convo = conversation(planPrompt(req, pantry, shortlist));
            fallbackReason = "the LLM did not produce a valid plan";
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                String reply;
                try {
                    reply = llm.complete(convo);
                } catch (LLMException e) {
                    trace.add("Attempt " + attempt + ": LLM call failed - " + e.getMessage());
                    fallbackReason = "the AI service failed (" + e.getMessage() + ")";
                    break;
                }
                convo.add(ChatMessage.assistant(reply));
                List<String> problems = new ArrayList<>();
                MealPlan plan;
                String rationale;
                try {
                    JsonObject json = JsonExtractor.extractObject(reply);
                    rationale = JsonExtractor.string(json, "rationale", "");
                    plan = parsePlan(json, req, byId, problems);
                } catch (RuntimeException e) { // malformed model output
                    trace.add("Attempt " + attempt + ": unusable reply - " + e.getMessage());
                    convo.add(ChatMessage.user("Your reply could not be used (" + e.getMessage()
                        + "). Reply again with the complete JSON plan only."));
                    continue;
                }
                ValidationResult v = validator.validate(plan, req);
                problems.forEach(v::addError);
                if (v.isValid()) {
                    trace.add("Attempt " + attempt + ": LLM plan with " + plan.getScheduledMealCount()
                        + " meals passed validation (" + Quantities.money(plan.getEstimatedCost()) + ")");
                    return new PlanGenerationResult(plan, v, true, rationale, trace);
                }
                trace.add("Attempt " + attempt + ": LLM plan rejected - " + String.join("; ", v.getErrors()));
                convo.add(ChatMessage.user("The plan failed validation:\n- " + String.join("\n- ", v.getErrors())
                    + "\nRevise the plan and reply with the complete corrected JSON only."));
            }
        } else {
            fallbackReason = "no LLM is configured";
            trace.add("LLM not configured; assembling the plan deterministically");
        }
        MealPlan plan = assembleDeterministically(ranked, req);
        ValidationResult v = validator.validate(plan, req);
        trace.add("Deterministic " + strategy.name() + " assembly: " + plan.getScheduledMealCount() + " meals, "
            + Quantities.money(plan.getEstimatedCost()) + ", " + (v.isValid() ? "valid" : "invalid: "
            + String.join("; ", v.getErrors())));
        return new PlanGenerationResult(plan, v, false, "Assembled by the " + strategy.name()
            + " strategy because " + fallbackReason + ".", trace);
    }

    private String planPrompt(PlanRequest req, Pantry pantry, List<Recipe> shortlist) {
        StringBuilder p = new StringBuilder();
        double budget = req.getProfile().getWeeklyBudget() * req.getDays() / 7.0;
        p.append("Build a meal plan.\n\nUSER PROFILE\n").append(req.getProfile().describe())
            .append("\nPlanning priority: ").append(strategy.name()).append('\n');
        appendInstruction(p, req);
        p.append("\nSLOTS TO FILL (").append(req.slotCount()).append(", ").append(req.getServingsPerMeal())
            .append(" servings each)\n");
        for (Slot s : slots(req)) {
            p.append(s.day()).append(' ').append(s.type()).append('\n');
        }
        p.append("\nBUDGET: ").append(budget > 0 ? "at most " + Quantities.money(budget)
            + " in total (cost per serving x " + req.getServingsPerMeal() + " for each meal)" : "no limit")
            .append("\n\nPANTRY (usable)\n").append(pantrySummary(pantry))
            .append("\nCANDIDATE RECIPES (filtered for restrictions, pre-ranked by the ").append(strategy.name())
            .append(" strategy; prefer higher entries)\n");
        for (Recipe r : shortlist) {
            p.append(candidateLine(r, req, pantry)).append('\n');
        }
        p.append("\nRules: fill every slot exactly once using only candidate recipeIds; use a recipe at most ")
            .append(PlanValidator.MAX_REPEATS).append(" times; recipes tagged breakfast only for BREAKFAST; "
                + "stay within budget; use soon-expiring pantry items early in the week.\n"
                + "JSON format:\n{\"meals\":[{\"day\":\"MONDAY\",\"mealType\":\"DINNER\",\"recipeId\":\"r01\"}],"
                + "\"rationale\":\"one or two sentences\"}");
        return p.toString();
    }

    /** Converts the LLM's plan JSON into a MealPlan; semantic problems are collected, not thrown. */
    MealPlan parsePlan(JsonObject json, PlanRequest req, Map<String, Recipe> byId, List<String> problems) {
        MealPlan plan = new MealPlan(req.getWeekStart());
        Set<Slot> wanted = new LinkedHashSet<>(slots(req));
        for (JsonElement e : array(json, "meals")) {
            JsonObject o = e.getAsJsonObject();
            DayOfWeek day;
            MealType type;
            try {
                day = DayOfWeek.valueOf(JsonExtractor.string(o, "day", "").trim().toUpperCase(Locale.ROOT));
                type = MealType.parse(JsonExtractor.string(o, "mealType", ""));
            } catch (IllegalArgumentException ex) {
                problems.add("Invalid day or meal type in " + o);
                continue;
            }
            String id = JsonExtractor.string(o, "recipeId", "");
            Recipe recipe = byId.get(id);
            if (recipe == null) {
                problems.add("Unknown recipeId '" + id + "' (use only candidate ids)");
                continue;
            }
            if (!wanted.contains(new Slot(day, type))) {
                problems.add(day + " " + type + " was not requested");
                continue;
            }
            if (plan.getMeal(day, type) != null) {
                problems.add(day + " " + type + " was assigned twice");
                continue;
            }
            plan.addMeal(new PlannedMeal(day, type, recipe, req.getServingsPerMeal()));
        }
        return plan;
    }

    /**
     * Greedy, budget-aware assembly from the strategy ranking: for each slot
     * pick the best suitable recipe that keeps the remaining slots affordable,
     * preferring recipes not used yet.
     */
    MealPlan assembleDeterministically(List<Recipe> ranked, PlanRequest req) {
        MealPlan plan = new MealPlan(req.getWeekStart());
        List<Slot> slots = slots(req);
        double budget = req.getProfile().getWeeklyBudget() * req.getDays() / 7.0;
        Map<String, Integer> uses = new HashMap<>();
        double spent = 0;
        // Lower bound of the cost of filling each remaining slot with its cheapest suitable recipe.
        Map<MealType, Double> cheapest = new HashMap<>();
        for (MealType type : req.getMealTypes()) {
            cheapest.put(type, suitableFor(ranked, type).stream()
                .mapToDouble(r -> r.getCostPerServing() * req.getServingsPerMeal()).min().orElse(0));
        }
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            List<Recipe> suitable = suitableFor(ranked, slot.type());
            double remainingMin = 0;
            for (Slot later : slots.subList(i + 1, slots.size())) {
                remainingMin += cheapest.get(later.type());
            }
            Recipe pick = null;
            for (int pass = 0; pass < 2 && pick == null; pass++) {
                for (Recipe r : suitable) {
                    int used = uses.getOrDefault(r.getId(), 0);
                    if (used >= PlanValidator.MAX_REPEATS || (pass == 0 && used > 0)) {
                        continue;
                    }
                    double cost = r.getCostPerServing() * req.getServingsPerMeal();
                    if (budget <= 0 || spent + cost + remainingMin <= budget + 1e-9) {
                        pick = r;
                        break;
                    }
                }
            }
            if (pick == null) {
                // Nothing fits the budget: take the cheapest allowed recipe; the validator will report it.
                pick = suitable.stream().filter(r -> uses.getOrDefault(r.getId(), 0) < PlanValidator.MAX_REPEATS)
                    .min((a, b) -> Double.compare(a.getCostPerServing(), b.getCostPerServing())).orElse(null);
            }
            if (pick == null) {
                continue;
            }
            uses.merge(pick.getId(), 1, Integer::sum);
            spent += pick.getCostPerServing() * req.getServingsPerMeal();
            plan.addMeal(new PlannedMeal(slot.day(), slot.type(), pick, req.getServingsPerMeal()));
        }
        return plan;
    }

    // ------------------------------------------------------------------ F08

    /**
     * F08: proposes substitutes for one ingredient of a recipe. Curated
     * substitutes are retrieved first and given to the LLM as grounding; every
     * proposal is checked against the user's restrictions and dislikes and
     * marked if it is already in the pantry. The recipe itself is not changed.
     *
     * @throws IllegalArgumentException if the ingredient is not part of the recipe
     */
    public SubstitutionResult suggestSubstitutions(Recipe recipe, String ingredient, UserProfile profile,
                                                   Pantry pantry) {
        if (recipe == null || ingredient == null || ingredient.isBlank()) {
            throw new IllegalArgumentException("A recipe and an ingredient are required");
        }
        if (!recipe.containsIngredient(ingredient)) {
            throw new IllegalArgumentException(recipe.getName() + " does not contain '" + ingredient.trim() + "'");
        }
        AgentTrace trace = new AgentTrace();
        List<SubstitutionCatalog.Entry> curated = SubstitutionCatalog.lookup(ingredient);
        trace.add("Retrieved " + curated.size() + " curated substitute(s) for " + ingredient);
        Map<String, String> proposals = new LinkedHashMap<>();
        boolean aiUsed = false;
        String message = "Suggestions are informational; the recipe was not changed. Allergen safety is not "
            + "guaranteed.";
        if (llm.isAvailable()) {
            StringBuilder p = new StringBuilder("Suggest up to 4 substitutes for '").append(ingredient)
                .append("' in the recipe '").append(recipe.getName()).append("'.\nRecipe ingredients: ")
                .append(String.join(", ", recipe.getIngredients())).append("\n\nUSER PROFILE\n")
                .append(profile.describe()).append("\n\nPANTRY (usable)\n").append(pantrySummary(pantry))
                .append("\nREFERENCE SUBSTITUTES (curated)\n");
            curated.forEach(c -> p.append("- ").append(c.name()).append(": ").append(c.note()).append('\n'));
            p.append("\nPrefer substitutes that fit the dish, respect the restrictions and are in the pantry. "
                + "JSON format:\n{\"substitutes\":[{\"name\":\"...\",\"note\":\"ratio and effect\"}]}");
            try {
                JsonObject json = JsonExtractor.extractObject(llm.complete(conversation(p.toString())));
                for (JsonElement e : array(json, "substitutes")) {
                    JsonObject o = e.getAsJsonObject();
                    String name = JsonExtractor.string(o, "name", "").trim();
                    if (!name.isEmpty()) {
                        proposals.putIfAbsent(name.toLowerCase(Locale.ROOT), name + "\u0000"
                            + JsonExtractor.string(o, "note", ""));
                    }
                }
                aiUsed = true;
                trace.add("LLM proposed " + proposals.size() + " substitute(s)");
            } catch (RuntimeException e) { // LLM failure or unusable model output
                trace.add("LLM step failed - " + e.getMessage());
                message = "AI suggestions unavailable (" + e.getMessage() + "); showing curated substitutes only. "
                    + message;
            }
        } else {
            trace.add("LLM not configured; using curated substitutes only");
            message = "AI suggestions are off (no LLM configured); showing curated substitutes only. " + message;
        }
        for (SubstitutionCatalog.Entry c : curated) {
            proposals.putIfAbsent(c.name().toLowerCase(Locale.ROOT), c.name() + "\u0000" + c.note());
        }
        List<Substitution> acceptable = new ArrayList<>();
        List<Substitution> rejected = new ArrayList<>();
        for (String value : proposals.values()) {
            String[] parts = value.split("\u0000", 2);
            String name = parts[0];
            String problem = substituteProblem(name, profile);
            Substitution s = new Substitution(name, parts.length > 1 ? parts[1] : "", pantry.hasUsable(name),
                problem.isEmpty(), problem);
            (problem.isEmpty() ? acceptable : rejected).add(s);
        }
        acceptable.sort((a, b) -> Boolean.compare(b.inPantry(), a.inPantry()));
        trace.add("Checked proposals: " + acceptable.size() + " acceptable, " + rejected.size() + " rejected");
        if (acceptable.isEmpty() && rejected.isEmpty()) {
            message = "No substitute is known for '" + ingredient + "'. " + message;
        }
        List<Substitution> all = new ArrayList<>(acceptable);
        all.addAll(rejected);
        return new SubstitutionResult(ingredient.trim(), all, aiUsed, message, trace);
    }

    private static String substituteProblem(String name, UserProfile profile) {
        for (String restriction : profile.getRestrictions()) {
            if (DietaryRules.violates(restriction, name)) {
                return "conflicts with " + restriction;
            }
        }
        for (String disliked : profile.getDislikedIngredients()) {
            if (DietaryRules.containsWord(name.toLowerCase(Locale.ROOT), DietaryRules.customKeyword(disliked))) {
                return "you dislike " + disliked;
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ F09

    /**
     * F09: proposes a recipe that uses the given (non-expired) leftovers. The
     * agent retrieves catalogue recipes that use them, lets the LLM choose one
     * or decide that a new recipe is needed, and then generates and validates
     * that recipe. Without an LLM the best catalogue match is returned.
     */
    public LeftoverPlanResult planFromLeftovers(List<PantryItem> leftovers, PlanRequest req, Pantry pantry) {
        AgentTrace trace = new AgentTrace();
        List<PantryItem> usable = new ArrayList<>();
        for (PantryItem item : leftovers) {
            if (!item.isExpired() && item.getAmount() > 0) {
                usable.add(item);
            } else {
                trace.add("Skipped " + item.getIngredientName() + " (expired or empty)");
            }
        }
        if (usable.isEmpty()) {
            return new LeftoverPlanResult(null, List.of(), "There are no usable leftovers. Mark pantry items as "
                + "LEFTOVER to use this feature; expired leftovers are never suggested.", false, false, trace);
        }
        List<RecipeMatch> matches = new ArrayList<>();
        for (RecipeMatch m : recipeService.findUsing(usable, pantry)) {
            if (validator.isAcceptable(m.recipe(), req.getProfile())) {
                matches.add(m);
            }
        }
        trace.add("Found " + matches.size() + " acceptable catalogue recipe(s) using leftovers "
            + names(usable.stream().map(PantryItem::getIngredientName).toList()));

        if (llm.isAvailable()) {
            StringBuilder p = new StringBuilder("Choose the best way to use these leftovers.\n\nLEFTOVERS\n");
            usable.forEach(i -> p.append("- ").append(i).append('\n'));
            p.append("\nUSER PROFILE\n").append(req.getProfile().describe()).append('\n');
            appendInstruction(p, req);
            p.append("\nCATALOGUE RECIPES THAT USE THEM\n");
            matches.stream().limit(8).forEach(m -> p.append(m.recipe().getId()).append(" | ")
                .append(m.recipe().getName()).append(" | uses ").append(String.join(", ", m.matchedIngredients()))
                .append(" | missing ").append(m.missingIngredients().isEmpty() ? "nothing"
                    : String.join(", ", m.missingIngredients())).append('\n'));
            p.append("\nReply {\"choice\":\"<recipeId>\",\"reason\":\"...\"} to pick a catalogue recipe, or "
                + "{\"choice\":\"NEW\",\"reason\":\"...\"} if a new recipe would use the leftovers better.");
            try {
                JsonObject json = JsonExtractor.extractObject(llm.complete(conversation(p.toString())));
                String choice = JsonExtractor.string(json, "choice", "");
                String reason = JsonExtractor.string(json, "reason", "");
                RecipeMatch picked = matches.stream().filter(m -> m.recipe().getId().equalsIgnoreCase(choice))
                    .findFirst().orElse(null);
                if (picked != null) {
                    trace.add("LLM chose catalogue recipe " + picked.recipe().getName());
                    return new LeftoverPlanResult(picked.recipe(), picked.matchedIngredients(), reason, true,
                        false, trace);
                }
                if (!"NEW".equalsIgnoreCase(choice.trim()) && !matches.isEmpty()) {
                    trace.add("LLM chose unknown id '" + choice + "'; falling back to the best catalogue match");
                } else {
                    trace.add("LLM decided a new recipe is needed: " + reason);
                    String names = String.join(", ", usable.stream().map(PantryItem::getIngredientName).toList());
                    RecipeGenerationResult gen = generateRecipe(req, pantry, "Use up these leftovers: " + names
                        + (req.getUserInstruction().isBlank() ? "" : ". " + req.getUserInstruction()), trace);
                    if (gen.success()) {
                        List<String> used = usable.stream().map(PantryItem::getIngredientName)
                            .filter(gen.recipe()::containsIngredient).toList();
                        return new LeftoverPlanResult(gen.recipe(), used, reason, true, true, trace);
                    }
                    if (matches.isEmpty()) {
                        return new LeftoverPlanResult(null, List.of(), gen.message(), true, false, trace);
                    }
                }
            } catch (RuntimeException e) { // LLM failure or unusable model output
                trace.add("LLM step failed - " + e.getMessage());
            }
        } else {
            trace.add("LLM not configured; choosing the best catalogue match deterministically");
        }
        if (matches.isEmpty()) {
            return new LeftoverPlanResult(null, List.of(), "No catalogue recipe uses your leftovers"
                + (llm.isAvailable() ? "." : "; configure an LLM so the agent can create one."), false, false, trace);
        }
        RecipeMatch best = matches.get(0);
        return new LeftoverPlanResult(best.recipe(), best.matchedIngredients(), "Best catalogue match: uses "
            + String.join(", ", best.matchedIngredients()) + (best.missingIngredients().isEmpty()
                ? " and needs nothing else." : "; also needs " + String.join(", ", best.missingIngredients()) + "."),
            false, false, trace);
    }

    // ------------------------------------------------------------------ F11

    /**
     * F11: interprets a natural-language instruction as a structured
     * {@link PlanEdit} (REPLACE or REMOVE) against the current plan. The
     * LLM's answer is verified (target slot exists, replacement recipe exists
     * and satisfies restrictions); invalid answers get one revision request.
     * Without an LLM, the rule-based {@link PlanEditParser} is used. This
     * method never modifies the plan.
     */
    public EditInterpretation interpretPlanEdit(MealPlan plan, String text, UserProfile profile) {
        AgentTrace trace = new AgentTrace();
        if (plan == null || plan.getScheduledMealCount() == 0) {
            return new EditInterpretation(null, "There is no active meal plan. Generate a plan first.", false, trace);
        }
        if (text == null || text.isBlank()) {
            return new EditInterpretation(null, "Type an instruction such as \"replace Tuesday dinner with "
                + "something vegetarian\".", false, trace);
        }
        if (llm.isAvailable()) {
            StringBuilder p = new StringBuilder("Interpret the user's change to their meal plan.\n\nCURRENT PLAN\n");
            for (PlannedMeal m : plan.getMeals()) {
                p.append(m.getDay()).append(' ').append(m.getMealType()).append(": ").append(m.getRecipe().getId())
                    .append(" ").append(m.getRecipe().getName()).append('\n');
            }
            p.append("\nAVAILABLE RECIPES (already filtered for the user's restrictions)\n");
            for (Recipe r : recipeService.getAllRecipes()) {
                if (validator.isAcceptable(r, profile)) {
                    p.append(r.getId()).append(": ").append(r.getName()).append(" [").append(String.join(",",
                        r.getTags())).append("] ").append(Quantities.money(r.getCostPerServing())).append("/serving\n");
                }
            }
            p.append("\nUSER INSTRUCTION: ").append(text.trim()).append("\n\nOnly REPLACE or REMOVE of one existing "
                + "meal is supported. If the instruction is ambiguous or unsupported use operation UNSUPPORTED and "
                + "explain. JSON format:\n{\"operation\":\"REPLACE|REMOVE|UNSUPPORTED\",\"day\":\"MONDAY\","
                + "\"mealType\":\"DINNER\",\"replacementRecipeId\":\"r09 or null\",\"explanation\":\"...\"}");
            List<ChatMessage> convo = conversation(p.toString());
            for (int attempt = 1; attempt <= 2; attempt++) {
                String reply;
                try {
                    reply = llm.complete(convo);
                } catch (LLMException e) {
                    trace.add("LLM call failed - " + e.getMessage() + "; trying the rule-based parser");
                    break;
                }
                convo.add(ChatMessage.assistant(reply));
                try {
                    JsonObject json = JsonExtractor.extractObject(reply);
                    String op = JsonExtractor.string(json, "operation", "").trim().toUpperCase(Locale.ROOT);
                    String explanation = JsonExtractor.string(json, "explanation", "");
                    if (op.equals("UNSUPPORTED")) {
                        trace.add("LLM marked the instruction as unsupported/ambiguous");
                        return new EditInterpretation(null, explanation.isBlank()
                            ? "That change is not supported." : explanation, true, trace);
                    }
                    PlanEdit edit = toPlanEdit(json, op, plan, profile);
                    trace.add("Attempt " + attempt + ": LLM interpreted as " + edit.describe());
                    return new EditInterpretation(edit, explanation.isBlank() ? edit.describe() : explanation, true,
                        trace);
                } catch (RuntimeException e) { // invalid interpretation or malformed output
                    trace.add("Attempt " + attempt + ": rejected interpretation - " + e.getMessage());
                    convo.add(ChatMessage.user("That interpretation is invalid: " + e.getMessage()
                        + ". Reply again with corrected JSON only."));
                }
            }
            if (convo.size() > 2) {
                return new EditInterpretation(null, "The AI could not produce a valid edit; the plan was not changed. "
                    + "Try naming the day and meal, e.g. \"replace Monday dinner with Greek Salad\".", true, trace);
            }
        } else {
            trace.add("LLM not configured; using the rule-based parser");
        }
        PlanEditParser.Outcome outcome = PlanEditParser.parse(text, plan, recipeService, profile, validator);
        trace.add("Rule-based parser: " + outcome.message());
        return new EditInterpretation(outcome.edit(), outcome.message(), false, trace);
    }

    private PlanEdit toPlanEdit(JsonObject json, String op, MealPlan plan, UserProfile profile) {
        EditOperation operation;
        try {
            operation = EditOperation.valueOf(op);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("operation must be REPLACE, REMOVE or UNSUPPORTED");
        }
        DayOfWeek day;
        try {
            day = DayOfWeek.valueOf(JsonExtractor.string(json, "day", "").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("day must be a weekday name such as MONDAY");
        }
        MealType type = MealType.parse(JsonExtractor.string(json, "mealType", ""));
        PlannedMeal target = plan.getMeal(day, type);
        if (target == null) {
            throw new IllegalArgumentException("there is no meal planned for " + day + " " + type);
        }
        if (operation == EditOperation.REMOVE) {
            return new PlanEdit(EditOperation.REMOVE, target, null);
        }
        String id = JsonExtractor.string(json, "replacementRecipeId", "");
        Recipe replacement = recipeService.getById(id).orElseThrow(
            () -> new IllegalArgumentException("unknown replacementRecipeId '" + id + "'"));
        ValidationResult check = validator.checkRecipe(replacement, profile);
        if (!check.isValid()) {
            throw new IllegalArgumentException(String.join("; ", check.getErrors()));
        }
        if (replacement.equals(target.getRecipe())) {
            throw new IllegalArgumentException("the replacement is the same recipe that is already planned");
        }
        return new PlanEdit(EditOperation.REPLACE, target,
            new PlannedMeal(day, type, replacement, target.getServings()));
    }

    // ------------------------------------------------------------------ helpers

    /** Recipes passing {@link PlanValidator#checkRecipe}; exclusions are noted. */
    private List<Recipe> eligibleRecipes(UserProfile profile, AgentTrace trace, List<String> notes) {
        List<Recipe> eligible = new ArrayList<>();
        int excluded = 0;
        for (Recipe r : recipeService.getAllRecipes()) {
            if (validator.isAcceptable(r, profile)) {
                eligible.add(r);
            } else {
                excluded++;
            }
        }
        trace.add("Retrieved " + recipeService.size() + " recipes; " + excluded
            + " excluded by restrictions/dislikes");
        if (excluded > 0) {
            notes.add(excluded + " recipe(s) were excluded because they conflict with your restrictions or dislikes.");
        }
        return eligible;
    }

    /** Top candidates per requested meal type, so breakfast slots always have options. */
    private List<Recipe> shortlist(List<Recipe> ranked, PlanRequest req) {
        Set<Recipe> list = new LinkedHashSet<>();
        for (MealType type : req.getMealTypes()) {
            suitableFor(ranked, type).stream().limit(Math.max(8, req.getDays() + 2)).forEach(list::add);
        }
        return new ArrayList<>(list);
    }

    /** Recipes suited to a meal type (breakfast-tagged for breakfast, others otherwise); all if none fit. */
    private static List<Recipe> suitableFor(List<Recipe> ranked, MealType type) {
        List<Recipe> out = new ArrayList<>();
        for (Recipe r : ranked) {
            if (r.hasTag("breakfast") == (type == MealType.BREAKFAST)) {
                out.add(r);
            }
        }
        return out.isEmpty() ? ranked : out;
    }

    static List<Slot> slots(PlanRequest req) {
        List<Slot> slots = new ArrayList<>();
        DayOfWeek first = req.getWeekStart().getDayOfWeek();
        for (int d = 0; d < req.getDays(); d++) {
            for (MealType type : MealType.values()) {
                if (req.getMealTypes().contains(type)) {
                    slots.add(new Slot(first.plus(d), type));
                }
            }
        }
        return slots;
    }

    /** One line describing a candidate for prompts. */
    private String candidateLine(Recipe r, PlanRequest req, Pantry pantry) {
        List<String> expiring = PantryFirstStrategy.expiringUsed(r, pantry);
        return String.format(Locale.ROOT, "%s | %s | %s/serving | %d min | tags: %s | pantry match %.0f%%%s",
            r.getId(), r.getName(), Quantities.money(r.getCostPerServing()), r.getCookingTimeMinutes(),
            String.join(",", r.getTags()), matcher.getMatchPercentage(r, pantry),
            expiring.isEmpty() ? "" : " | uses expiring: " + String.join(", ", expiring));
    }

    private static String pantrySummary(Pantry pantry) {
        StringBuilder sb = new StringBuilder();
        for (PantryItem item : pantry.getItems()) {
            if (!item.isExpired() && item.getAmount() > 0) {
                sb.append("- ").append(item).append('\n');
            }
        }
        return sb.length() == 0 ? "(empty)\n" : sb.toString();
    }

    private static void appendInstruction(StringBuilder sb, PlanRequest req) {
        if (!req.getUserInstruction().isBlank()) {
            sb.append("User instruction: ").append(req.getUserInstruction()).append('\n');
        }
    }

    private static List<ChatMessage> conversation(String prompt) {
        List<ChatMessage> convo = new ArrayList<>();
        convo.add(ChatMessage.system(SYSTEM_PROMPT));
        convo.add(ChatMessage.user(prompt));
        return convo;
    }

    private String unavailableHint() {
        return "Current LLM: " + llm.describe() + ".";
    }

    private static JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) {
            throw new LLMException("missing '" + key + "' array");
        }
        return e.getAsJsonArray();
    }

    private static int intValue(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return (int) Math.round(e.getAsDouble());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String names(List<?> items) {
        List<String> out = new ArrayList<>();
        for (Object o : items) {
            out.add(o instanceof Recipe r ? r.getName() : String.valueOf(o));
        }
        return String.join(", ", out);
    }

    /** One requested (day, meal type) slot. */
    record Slot(DayOfWeek day, MealType type) {
    }
}
