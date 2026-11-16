package mealplanner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable recipe with measured ingredients, preparation steps, an
 * approximate cost and dietary tags.
 *
 * <p><b>Builder pattern:</b> recipes have many optional attributes (tags, cost,
 * source, id) and are created from three very different places - the JSON
 * catalogue, LLM output and scaling. {@link Builder} centralises validation so
 * every creation path produces a consistent object.</p>
 */
public final class Recipe {

    /** Where the recipe came from. AI-generated recipes are never presented as verified. */
    public enum Source { CATALOGUE, AI_GENERATED }

    private final String id;
    private final String name;
    private final List<IngredientRequirement> requirements;
    private final List<String> steps;
    private final int cookingTimeMinutes;
    private final int servings;
    private final double estimatedCost;
    private final Set<String> tags;
    private final Source source;

    private Recipe(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.requirements = List.copyOf(b.requirements);
        this.steps = List.copyOf(b.steps);
        this.cookingTimeMinutes = b.cookingTimeMinutes;
        this.servings = b.servings;
        this.estimatedCost = b.estimatedCost;
        this.tags = Collections.unmodifiableSet(new LinkedHashSet<>(b.tags));
        this.source = b.source;
    }

    /** Starts building a recipe with the given display name. */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** Ingredient names in recipe order. */
    public List<String> getIngredients() {
        List<String> names = new ArrayList<>();
        for (IngredientRequirement r : requirements) {
            names.add(r.getName());
        }
        return names;
    }

    public List<IngredientRequirement> getIngredientRequirements() {
        return requirements;
    }

    public List<String> getSteps() {
        return steps;
    }

    public int getCookingTimeMinutes() {
        return cookingTimeMinutes;
    }

    /** Number of portions the ingredient quantities are written for. */
    public int getServings() {
        return servings;
    }

    /** Approximate total cost (CAD) for {@link #getServings()} portions. Not a live price. */
    public double getEstimatedCost() {
        return estimatedCost;
    }

    public double getCostPerServing() {
        return estimatedCost / servings;
    }

    /** Lower-case dietary / descriptive tags such as "vegetarian" or "quick". */
    public Set<String> getTags() {
        return tags;
    }

    public boolean hasTag(String tag) {
        return tag != null && tags.contains(tag.trim().toLowerCase(Locale.ROOT));
    }

    public Source getSource() {
        return source;
    }

    /** True if any ingredient name equals {@code ingredientName}, ignoring case. */
    public boolean containsIngredient(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return false;
        }
        for (IngredientRequirement r : requirements) {
            if (r.hasName(ingredientName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * F07: returns a copy of this recipe whose quantities and cost are
     * proportionally recalculated for {@code targetServings} portions.
     * The original recipe is not modified.
     *
     * @throws IllegalArgumentException if targetServings is not positive
     */
    public Recipe scaleTo(int targetServings) {
        if (targetServings <= 0) {
            throw new IllegalArgumentException("Servings must be a positive whole number");
        }
        double factor = (double) targetServings / servings;
        Builder b = toBuilder().servings(targetServings).estimatedCost(estimatedCost * factor);
        b.requirements.clear();
        for (IngredientRequirement r : requirements) {
            b.requirements.add(r.scale(factor));
        }
        return b.build();
    }

    /** Creates a builder pre-populated with this recipe's values. */
    public Builder toBuilder() {
        Builder b = new Builder(name).id(id).cookingTimeMinutes(cookingTimeMinutes)
            .servings(servings).estimatedCost(estimatedCost).source(source);
        b.requirements.addAll(requirements);
        b.steps.addAll(steps);
        b.tags.addAll(tags);
        return b;
    }

    /** Multi-line human-readable description used by both GUI and CLI. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("  [id: ").append(id).append("]\n");
        sb.append("Serves ").append(servings).append(" | ").append(cookingTimeMinutes)
          .append(" min | approx. ").append(Quantities.money(estimatedCost))
          .append(" (").append(Quantities.money(getCostPerServing())).append("/serving)\n");
        if (!tags.isEmpty()) {
            sb.append("Tags: ").append(String.join(", ", tags)).append('\n');
        }
        if (source == Source.AI_GENERATED) {
            sb.append("Source: AI-generated (not verified; check allergens yourself)\n");
        }
        sb.append("Ingredients:\n");
        for (IngredientRequirement r : requirements) {
            sb.append("  - ").append(r).append('\n');
        }
        sb.append("Steps:\n");
        for (int i = 0; i < steps.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(steps.get(i)).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Recipe)) {
            return false;
        }
        Recipe other = (Recipe) o;
        return id.equals(other.id) && servings == other.servings;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, servings);
    }

    /** Builder that validates all recipe invariants in {@link #build()}. */
    public static final class Builder {
        private String id;
        private final String name;
        private final List<IngredientRequirement> requirements = new ArrayList<>();
        private final List<String> steps = new ArrayList<>();
        private int cookingTimeMinutes = 30;
        private int servings = 2;
        private double estimatedCost;
        private final Set<String> tags = new LinkedHashSet<>();
        private Source source = Source.CATALOGUE;

        private Builder(String name) {
            this.name = name == null ? null : name.trim();
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder ingredient(String ingredientName, double quantity, String unit) {
            requirements.add(new IngredientRequirement(ingredientName, quantity, unit));
            return this;
        }

        public Builder ingredient(IngredientRequirement requirement) {
            requirements.add(Objects.requireNonNull(requirement, "Ingredient cannot be null"));
            return this;
        }

        public Builder step(String step) {
            if (step == null || step.isBlank()) {
                throw new IllegalArgumentException("Recipe steps cannot be blank");
            }
            steps.add(step.trim());
            return this;
        }

        public Builder cookingTimeMinutes(int minutes) {
            this.cookingTimeMinutes = minutes;
            return this;
        }

        public Builder servings(int servings) {
            this.servings = servings;
            return this;
        }

        public Builder estimatedCost(double cost) {
            this.estimatedCost = cost;
            return this;
        }

        public Builder tag(String tag) {
            if (tag != null && !tag.isBlank()) {
                tags.add(tag.trim().toLowerCase(Locale.ROOT));
            }
            return this;
        }

        public Builder source(Source source) {
            this.source = Objects.requireNonNull(source);
            return this;
        }

        /**
         * Validates and creates the recipe.
         *
         * @throws IllegalArgumentException if the name is blank, there are no
         *         ingredients, or times/servings/cost are invalid
         */
        public Recipe build() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Recipe name cannot be empty");
            }
            if (requirements.isEmpty()) {
                throw new IllegalArgumentException("Recipe must have at least one ingredient");
            }
            if (cookingTimeMinutes <= 0 || servings <= 0) {
                throw new IllegalArgumentException("Cooking time and servings must be positive");
            }
            if (!Double.isFinite(estimatedCost) || estimatedCost < 0) {
                throw new IllegalArgumentException("Estimated cost must be a non-negative number");
            }
            if (id == null || id.isBlank()) {
                id = slug(name);
            }
            id = id.trim();
            return new Recipe(this);
        }

        private static String slug(String text) {
            String s = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            return s.replaceAll("^-|-$", "");
        }
    }
}
