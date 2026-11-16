package mealplanner.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The user's food preferences, disliked ingredients, dietary restrictions and
 * weekly food budget (Stage 1 F01).
 *
 * <p>All setters validate their input first and leave the stored state
 * unchanged when a value is rejected.</p>
 */
public class UserProfile {

    private List<String> preferences = new ArrayList<>();
    private Set<String> dislikedIngredients = new LinkedHashSet<>();
    private Set<String> restrictions = new LinkedHashSet<>();
    private double weeklyBudget;

    /** Creates a profile with an initial weekly budget. */
    public UserProfile(double weeklyBudget) {
        setWeeklyBudget(weeklyBudget);
    }

    /** Replaces the preferences (liked cuisines, ingredients, styles). Blank entries are dropped. */
    public void updatePreferences(List<String> values) {
        if (values == null) {
            throw new IllegalArgumentException("Preferences cannot be null");
        }
        preferences = new ArrayList<>(clean(values));
    }

    /** Replaces the dietary restrictions (e.g. vegetarian, nut-free). Stored lower-case, no duplicates. */
    public void setRestrictions(Set<String> values) {
        if (values == null) {
            throw new IllegalArgumentException("Restrictions cannot be null");
        }
        Set<String> cleaned = clean(values);
        if (cleaned.contains("vegan") && cleaned.stream().anyMatch(r -> r.contains("carnivore"))) {
            throw new IllegalArgumentException("Contradictory restrictions: vegan and carnivore");
        }
        restrictions = cleaned;
    }

    /** Replaces the list of ingredients the user does not want to eat. */
    public void setDislikedIngredients(Set<String> values) {
        if (values == null) {
            throw new IllegalArgumentException("Disliked ingredients cannot be null");
        }
        dislikedIngredients = clean(values);
    }

    /** Validates the weekly budget before saving it. Negative, infinite and NaN values are rejected. */
    public void setWeeklyBudget(double budget) {
        if (!Double.isFinite(budget) || budget < 0) {
            throw new IllegalArgumentException("Budget must be a non-negative finite number");
        }
        weeklyBudget = budget;
    }

    public List<String> getPreferences() {
        return new ArrayList<>(preferences);
    }

    public Set<String> getRestrictions() {
        return new LinkedHashSet<>(restrictions);
    }

    public Set<String> getDislikedIngredients() {
        return new LinkedHashSet<>(dislikedIngredients);
    }

    public double getWeeklyBudget() {
        return weeklyBudget;
    }

    /** One-paragraph summary used in prompts and in the user interfaces. */
    public String describe() {
        return "Weekly budget: " + Quantities.money(weeklyBudget)
            + "\nPreferences: " + listOrNone(preferences)
            + "\nDisliked ingredients: " + listOrNone(dislikedIngredients)
            + "\nDietary restrictions: " + listOrNone(restrictions);
    }

    private static String listOrNone(java.util.Collection<String> values) {
        return values.isEmpty() ? "(none)" : String.join(", ", values);
    }

    private static Set<String> clean(java.util.Collection<String> values) {
        Set<String> out = new LinkedHashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(v.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }
}
