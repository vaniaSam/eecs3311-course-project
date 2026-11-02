import java.util.ArrayList;

import java.util.HashSet;

import java.util.List;

import java.util.Set;

/**

 * Stores the user's food preferences, dietary restrictions,

 * and weekly meal-planning budget.

 *

 * Supports Stage 1 feature F01.

 */

public class UserProfile {

    // User preferences, such as preferred cuisines or ingredients.

    private List<String> preferences = new ArrayList<>();

    // Dietary restrictions are stored as a set to avoid duplicates.

    private Set<String> restrictions = new HashSet<>();

    // Maximum amount the user wants to spend on food per week.

    private double weeklyBudget;

    /**

     * Creates a profile with an initial weekly budget.

     */

    public UserProfile(double weeklyBudget) {

        setWeeklyBudget(weeklyBudget);

    }

    /**

     * Updates the user's food preferences.

     * Copies the input to prevent external modification.

     */

    public void updatePreferences(List<String> values) {

        if (values == null) {

            throw new IllegalArgumentException(

                "Preferences cannot be null"

            );

        }

        preferences = new ArrayList<>(values);

    }

    /**

     * Updates dietary restrictions without allowing duplicates.

     */

    public void setRestrictions(Set<String> values) {

        if (values == null) {

            throw new IllegalArgumentException(

                "Restrictions cannot be null"

            );

        }

        restrictions = new HashSet<>(values);

    }

    /**

     * Validates the weekly budget before saving it.

     * Negative, infinite, and NaN values are not accepted.

     */

    public void setWeeklyBudget(double budget) {

        if (!Double.isFinite(budget) || budget < 0) {

            throw new IllegalArgumentException(

                "Budget must be a non-negative finite number"

            );

        }

        weeklyBudget = budget;

    }

    // Return defensive copies so callers cannot modify stored data.

    public List<String> getPreferences() {

        return new ArrayList<>(preferences);

    }

    public Set<String> getRestrictions() {

        return new HashSet<>(restrictions);

    }

    public double getWeeklyBudget() {

        return weeklyBudget;
    }
}