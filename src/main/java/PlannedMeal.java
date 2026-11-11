
import java.time.DayOfWeek;
import java.util.Objects;

/**
 * Represents one scheduled meal in a weekly meal plan.
 */
public class PlannedMeal {

    public enum MealType {
        BREAKFAST, LUNCH, DINNER
    }

    private final DayOfWeek day;
    private final MealType mealType;
    private final Recipe recipe;

    public PlannedMeal(
            DayOfWeek day,
            MealType mealType,
            Recipe recipe) {

        this.day = Objects.requireNonNull(day, "Day cannot be null");
        this.mealType = Objects.requireNonNull(
            mealType, "Meal type cannot be null"
        );
        this.recipe = Objects.requireNonNull(
            recipe, "Recipe cannot be null"
        );
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
}

