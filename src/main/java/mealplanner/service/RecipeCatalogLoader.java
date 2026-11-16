package mealplanner.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import mealplanner.model.Recipe;

/**
 * Loads the seeded local recipe collection (Stage 1 section 1.6) from the
 * classpath resource {@code /data/recipes.json}. Every entry goes through
 * {@link Recipe.Builder}, so invalid catalogue data fails fast at start-up.
 */
public final class RecipeCatalogLoader {

    /** Default classpath location of the seeded catalogue. */
    public static final String DEFAULT_RESOURCE = "/data/recipes.json";

    private RecipeCatalogLoader() {
    }

    /** Loads the default catalogue. */
    public static List<Recipe> loadDefault() {
        try (InputStream in = RecipeCatalogLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Recipe catalogue not found on classpath: " + DEFAULT_RESOURCE);
            }
            return load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read recipe catalogue", e);
        }
    }

    /**
     * Parses a catalogue in the {@code {"recipes":[...]}} format.
     *
     * @throws IllegalStateException if the JSON is malformed or a recipe is invalid
     */
    public static List<Recipe> load(Reader reader) {
        try {
            Catalogue catalogue = new Gson().fromJson(reader, Catalogue.class);
            if (catalogue == null || catalogue.recipes == null) {
                throw new IllegalStateException("Recipe catalogue is empty");
            }
            List<Recipe> recipes = new ArrayList<>();
            for (RecipeDto dto : catalogue.recipes) {
                recipes.add(dto.toRecipe());
            }
            return recipes;
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid recipe catalogue: " + e.getMessage(), e);
        }
    }

    /** JSON shape of the catalogue file. */
    private static final class Catalogue {
        List<RecipeDto> recipes;
    }

    /** JSON shape of one recipe; shared with the LLM recipe parser via {@link #toRecipe()}. */
    static final class RecipeDto {
        String id;
        String name;
        int servings;
        int cookingTimeMinutes;
        double estimatedCost;
        List<String> tags;
        List<IngredientDto> ingredients;
        List<String> steps;

        Recipe toRecipe() {
            Recipe.Builder b = Recipe.builder(name).id(id).servings(servings)
                .cookingTimeMinutes(cookingTimeMinutes).estimatedCost(estimatedCost);
            if (ingredients != null) {
                for (IngredientDto i : ingredients) {
                    b.ingredient(i.name, i.quantity, i.unit);
                }
            }
            if (steps != null) {
                steps.forEach(b::step);
            }
            if (tags != null) {
                tags.forEach(b::tag);
            }
            return b.build();
        }
    }

    /** JSON shape of one ingredient. */
    static final class IngredientDto {
        String name;
        double quantity;
        String unit;
    }
}
