package mealplanner.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import mealplanner.model.MealPlan;
import mealplanner.model.PantryItem;
import mealplanner.model.Recipe;
import mealplanner.model.ShoppingList;
import mealplanner.model.UserProfile;
import mealplanner.model.ValidationResult;
import mealplanner.service.PantryService;
import mealplanner.service.RecipeMatch;
import mealplanner.service.RecipeService;

/**
 * Builds the read-only tool set for the {@link KitchenAssistant}. Each tool
 * wraps an existing service method, so the assistant reasons over the same
 * data and rules as the rest of the application.
 */
public final class KitchenTools {

    private KitchenTools() {
    }

    public static List<AgentTool> create(RecipeService recipes, PantryService pantry, PlanValidator validator,
                                         Supplier<UserProfile> profile, Supplier<MealPlan> plan,
                                         Supplier<ShoppingList> shoppingList) {
        List<AgentTool> tools = new ArrayList<>();
        tools.add(AgentTool.of("search_recipes", "keyword search over the recipe collection (names, ingredients, tags); not for the user's plan. "
            + "input: {\"query\":\"vegan pasta\"}", in -> {
                List<Recipe> found = recipes.search(JsonExtractor.string(in, "query", ""));
                return found.isEmpty() ? "No recipes found." : summaries(found);
            }));
        tools.add(AgentTool.of("recipes_with_ingredients", "recipes using any of the given ingredients, cookable "
            + "first. input: {\"ingredients\":\"egg, spinach\"}", in -> {
                List<String> names = new ArrayList<>();
                for (String s : JsonExtractor.string(in, "ingredients", "").split(",")) {
                    if (!s.isBlank()) {
                        names.add(s.trim());
                    }
                }
                if (names.isEmpty()) {
                    throw new IllegalArgumentException("ingredients is required");
                }
                List<RecipeMatch> found = recipes.findUsingNames(names, pantry.getPantry());
                StringBuilder sb = new StringBuilder();
                found.stream().limit(10).forEach(m -> sb.append(m.summary()).append('\n'));
                return found.isEmpty() ? "No recipes use those ingredients." : sb.toString();
            }));
        tools.add(AgentTool.of("get_recipe", "full recipe details. input: {\"id\":\"r03\"}", in -> {
            String id = JsonExtractor.string(in, "id", "");
            return recipes.findByIdOrName(id).map(Recipe::describe)
                .orElseThrow(() -> new IllegalArgumentException("unknown recipe id " + id));
        }));
        tools.add(AgentTool.of("check_recipe_for_user", "checks a recipe against the user's restrictions and "
            + "dislikes. input: {\"id\":\"r03\"}", in -> {
                String id = JsonExtractor.string(in, "id", "");
                Recipe r = recipes.findByIdOrName(id)
                    .orElseThrow(() -> new IllegalArgumentException("unknown recipe id " + id));
                ValidationResult v = validator.checkRecipe(r, profile.get());
                return v.isValid() ? "OK for this user (keyword check only). " + v.getWarnings()
                    : "NOT suitable: " + String.join("; ", v.getErrors());
            }));
        tools.add(AgentTool.of("list_pantry", "all pantry items with quantities and expiry dates. input: {}",
            in -> {
                StringBuilder sb = new StringBuilder();
                for (PantryItem i : pantry.getPantry().getItems()) {
                    sb.append(i).append(i.isExpired() ? " [EXPIRED]" : "").append('\n');
                }
                return sb.length() == 0 ? "The pantry is empty." : sb.toString();
            }));
        tools.add(AgentTool.of("expiring_items", "non-expired items expiring within N days. input: {\"days\":3}",
            in -> {
                int days = in.has("days") ? in.get("days").getAsInt() : 3;
                List<PantryItem> items = pantry.expiringWithin(days);
                return items.isEmpty() ? "Nothing expires within " + days + " days." : join(items);
            }));
        tools.add(AgentTool.of("list_leftovers", "usable leftovers. input: {}", in -> {
            List<PantryItem> items = pantry.findLeftovers();
            return items.isEmpty() ? "No usable leftovers." : join(items);
        }));
        tools.add(AgentTool.of("get_profile", "the user's budget, preferences, dislikes and restrictions. input: {}",
            in -> profile.get().describe()));
        tools.add(AgentTool.of("get_meal_plan", "the user's current meal plan (days, meals, recipe ids). input: {}", in -> {
            MealPlan p = plan.get();
            return p == null ? "No meal plan has been generated yet." : p.describe();
        }));
        tools.add(AgentTool.of("get_shopping_list", "the current shopping list for the plan. input: {}", in -> {
            ShoppingList list = shoppingList.get();
            return list == null ? "No meal plan, so no shopping list yet." : list.describe();
        }));
        return tools;
    }

    private static String summaries(List<Recipe> found) {
        StringBuilder sb = new StringBuilder();
        found.stream().limit(10).forEach(r -> sb.append(r.getId()).append(": ").append(r.getName()).append(" [")
            .append(String.join(",", r.getTags())).append("] ").append(r.getCookingTimeMinutes()).append(" min\n"));
        return sb.toString();
    }

    private static String join(List<?> items) {
        StringBuilder sb = new StringBuilder();
        items.forEach(i -> sb.append(i).append('\n'));
        return sb.toString();
    }
}
