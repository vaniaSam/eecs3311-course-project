package mealplanner.model;

import java.time.DayOfWeek;
import java.util.Locale;
import java.util.Objects;

/** One scheduled meal (day + meal type + recipe + servings) in a {@link MealPlan}. */
public class PlannedMeal {

    /** Meal slots supported by the planner. */
    public enum MealType {
        BREAKFAST, LUNCH, DINNER;

        /** Parses "dinner", "Dinner", "DINNER". */
        public static MealType parse(String text) {
            if (text == null) {
                throw new IllegalArgumentException("Meal type is required");
            }
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown meal type: " + text
                    + " (use breakfast, lunch or dinner)");
            }
        }
    }

    private final DayOfWeek day;
    private final MealType mealType;
    private final Recipe recipe;
    private final int servings;

    /** Creates a meal using the recipe's own serving count. */
    public PlannedMeal(DayOfWeek day, MealType mealType, Recipe recipe) {
        this(day, mealType, recipe, Objects.requireNonNull(recipe, "Recipe cannot be null").getServings());
    }

    /**
     * @param servings number of portions to cook; must be positive
     */
    public PlannedMeal(DayOfWeek day, MealType mealType, Recipe recipe, int servings) {
        this.day = Objects.requireNonNull(day, "Day cannot be null");
        this.mealType = Objects.requireNonNull(mealType, "Meal type cannot be null");
        this.recipe = Objects.requireNonNull(recipe, "Recipe cannot be null");
        if (servings <= 0) {
            throw new IllegalArgumentException("Servings must be positive");
        }
        this.servings = servings;
    }

    public DayOfWeek getDay() {
        return day;
    }

    public MealType getMealType() {
        return mealType;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public int getServings() {
        return servings;
    }

    /** Approximate cost of cooking this meal for {@link #getServings()} portions. */
    public double getEstimatedCost() {
        return recipe.getCostPerServing() * servings;
    }

    /** True if this meal occupies the given slot. */
    public boolean isSlot(DayOfWeek d, MealType t) {
        return day == d && mealType == t;
    }

    @Override
    public String toString() {
        return day + " " + mealType + ": " + recipe.getName() + " x" + servings;
    }
}
