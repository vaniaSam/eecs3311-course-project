package mealplanner.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import mealplanner.model.Pantry;
import mealplanner.model.PantryItem;
import mealplanner.model.Recipe;

/**
 * Recipe operations (Stage 1 RecipeService): the seeded recipe collection,
 * keyword search, ingredient-based search (F03), serving-size scaling (F07)
 * and storage of accepted AI-generated recipes (F05).
 *
 * <p>Replaces the earlier {@code RecipeSearch} class; its name/ingredient/time
 * search methods are kept.</p>
 */
public class RecipeService {

    private final Map<String, Recipe> recipes = new LinkedHashMap<>();
    private final RecipeMatcher matcher = new RecipeMatcher();

    public RecipeService() {
    }

    public RecipeService(Collection<Recipe> initial) {
        initial.forEach(this::addRecipe);
    }

    /**
     * Adds a recipe to the collection.
     *
     * @throws IllegalArgumentException if a recipe with the same id already exists
     */
    public void addRecipe(Recipe recipe) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe cannot be null");
        }
        if (recipes.containsKey(recipe.getId())) {
            throw new IllegalArgumentException("Duplicate recipe id: " + recipe.getId());
        }
        recipes.put(recipe.getId(), recipe);
    }

    /**
     * Saves an accepted AI-generated recipe, assigning a unique id if needed.
     *
     * @return the recipe as stored (its id may differ from the input)
     */
    public Recipe saveGenerated(Recipe recipe) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe cannot be null");
        }
        String base = recipe.getId().startsWith("ai-") ? recipe.getId() : "ai-" + recipe.getId();
        String id = base;
        int n = 2;
        while (recipes.containsKey(id)) {
            id = base + "-" + n++;
        }
        Recipe stored = recipe.toBuilder().id(id).source(Recipe.Source.AI_GENERATED).build();
        recipes.put(id, stored);
        return stored;
    }

    public List<Recipe> getAllRecipes() {
        return new ArrayList<>(recipes.values());
    }

    public int size() {
        return recipes.size();
    }

    /** Looks up a recipe by id (case-insensitive). */
    public Optional<Recipe> getById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Recipe exact = recipes.get(id.trim());
        if (exact != null) {
            return Optional.of(exact);
        }
        return recipes.values().stream().filter(r -> r.getId().equalsIgnoreCase(id.trim())).findFirst();
    }

    /** Looks up a recipe by id, or failing that by exact (case-insensitive) name. */
    public Optional<Recipe> findByIdOrName(String text) {
        Optional<Recipe> byId = getById(text);
        if (byId.isPresent() || text == null) {
            return byId;
        }
        return recipes.values().stream().filter(r -> r.getName().equalsIgnoreCase(text.trim())).findFirst();
    }

    /**
     * F03 keyword search: every whitespace-separated term must appear in the
     * recipe name, one of its ingredients or one of its tags.
     */
    public List<Recipe> search(String terms) {
        List<Recipe> results = new ArrayList<>();
        if (terms == null || terms.isBlank()) {
            return results;
        }
        String[] words = terms.toLowerCase(Locale.ROOT).trim().split("\\s+");
        for (Recipe recipe : recipes.values()) {
            String haystack = (recipe.getName() + " " + String.join(" ", recipe.getIngredients()) + " "
                + String.join(" ", recipe.getTags())).toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String w : words) {
                if (!haystack.contains(w)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                results.add(recipe);
            }
        }
        return results;
    }

    /** Recipes whose name contains the keyword (kept from the Stage 2 RecipeSearch class). */
    public List<Recipe> searchByName(String keyword) {
        List<Recipe> results = new ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            return results;
        }
        for (Recipe recipe : recipes.values()) {
            if (recipe.getName().toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                results.add(recipe);
            }
        }
        return results;
    }

    /** Recipes that contain the given ingredient. */
    public List<Recipe> searchByIngredient(String ingredient) {
        List<Recipe> results = new ArrayList<>();
        for (Recipe recipe : recipes.values()) {
            if (recipe.containsIngredient(ingredient)) {
                results.add(recipe);
            }
        }
        return results;
    }

    /** Recipes that can be prepared within {@code maxMinutes}. */
    public List<Recipe> filterByCookingTime(int maxMinutes) {
        List<Recipe> results = new ArrayList<>();
        if (maxMinutes <= 0) {
            return results;
        }
        for (Recipe recipe : recipes.values()) {
            if (recipe.getCookingTimeMinutes() <= maxMinutes) {
                results.add(recipe);
            }
        }
        return results;
    }

    /**
     * F03 ingredient-based search using pantry items. Recipes that use at least
     * one of the items are returned, fully cookable recipes first, then by the
     * number of matched items.
     */
    public List<RecipeMatch> findUsing(List<PantryItem> items, Pantry pantry) {
        if (items == null || pantry == null) {
            throw new IllegalArgumentException("Items and pantry are required");
        }
        List<String> names = new ArrayList<>();
        for (PantryItem item : items) {
            names.add(item.getIngredientName());
        }
        return findUsingNames(names, pantry);
    }

    /** F03 ingredient-based search using typed ingredient names. */
    public List<RecipeMatch> findUsingNames(List<String> ingredientNames, Pantry pantry) {
        if (ingredientNames == null || pantry == null) {
            throw new IllegalArgumentException("Ingredients and pantry are required");
        }
        List<RecipeMatch> matches = new ArrayList<>();
        for (Recipe recipe : recipes.values()) {
            List<String> used = new ArrayList<>();
            for (String name : ingredientNames) {
                if (recipe.containsIngredient(name) && used.stream().noneMatch(u -> u.equalsIgnoreCase(name.trim()))) {
                    used.add(name.trim());
                }
            }
            if (!used.isEmpty()) {
                matches.add(new RecipeMatch(recipe, used, matcher.getMissingIngredients(recipe, pantry),
                    matcher.isFullyCookable(recipe, pantry)));
            }
        }
        matches.sort(Comparator.comparing((RecipeMatch m) -> !m.fullyCookable())
            .thenComparing(m -> -m.matchedIngredients().size())
            .thenComparing(m -> m.missingIngredients().size()));
        return matches;
    }

    /**
     * F07: scales a recipe to a new serving count without modifying the stored recipe.
     *
     * @throws IllegalArgumentException for non-positive servings
     */
    public Recipe scale(Recipe recipe, int servings) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe is required");
        }
        return recipe.scaleTo(servings);
    }

    public RecipeMatcher getMatcher() {
        return matcher;
    }
}
