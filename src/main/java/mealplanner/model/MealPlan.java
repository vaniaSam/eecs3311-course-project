package mealplanner.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A weekly meal plan (Stage 1 F06).
 *
 * <p><b>Observer pattern - Subject role.</b> Registered {@link MealPlanObserver}s
 * are notified after every successful add, replace or remove, so dependent
 * views such as the shopping list stay synchronised without MealPlan knowing
 * how they are computed.</p>
 *
 * <p><b>Command pattern - Receiver role.</b> {@code ReplaceMealCommand} and
 * {@code RemoveMealCommand} call {@link #replaceMeal} / {@link #removeMeal} /
 * {@link #addMeal}.</p>
 */
public class MealPlan {

    private final LocalDate weekStart;
    private final List<PlannedMeal> meals = new ArrayList<>();
    private final List<MealPlanObserver> observers = new ArrayList<>();

    /** Creates an empty plan for the week beginning today. */
    public MealPlan() {
        this(LocalDate.now());
    }

    public MealPlan(LocalDate weekStart) {
        this.weekStart = Objects.requireNonNull(weekStart, "Week start cannot be null");
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    /** Registers an observer once. */
    public void attach(MealPlanObserver observer) {
        Objects.requireNonNull(observer, "Observer cannot be null");
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void detach(MealPlanObserver observer) {
        observers.remove(observer);
    }

    /** Notifies every observer (iterates over a copy so observers may detach themselves). */
    public void notifyObservers() {
        for (MealPlanObserver observer : new ArrayList<>(observers)) {
            observer.onMealPlanChanged(this);
        }
    }

    /**
     * Adds a meal to an empty slot.
     *
     * @throws IllegalStateException if the slot is already occupied
     */
    public void addMeal(PlannedMeal meal) {
        Objects.requireNonNull(meal, "Meal cannot be null");
        if (getMeal(meal.getDay(), meal.getMealType()) != null) {
            throw new IllegalStateException("Slot already used: " + meal.getDay() + " " + meal.getMealType());
        }
        meals.add(meal);
        notifyObservers();
    }

    /** Adds a meal or replaces whatever is in the same slot. */
    public void addOrReplaceMeal(PlannedMeal meal) {
        Objects.requireNonNull(meal, "Meal cannot be null");
        meals.removeIf(existing -> existing.isSlot(meal.getDay(), meal.getMealType()));
        meals.add(meal);
        notifyObservers();
    }

    /**
     * Replaces {@code old} with {@code next} at the same list position.
     *
     * @throws IllegalArgumentException if {@code old} is not in this plan
     */
    public void replaceMeal(PlannedMeal old, PlannedMeal next) {
        Objects.requireNonNull(next, "Replacement meal cannot be null");
        int index = meals.indexOf(old);
        if (index < 0) {
            throw new IllegalArgumentException("Meal is not part of this plan");
        }
        meals.set(index, next);
        notifyObservers();
    }

    /** Removes exactly this meal object. Returns true if it was present. */
    public boolean removeMeal(PlannedMeal meal) {
        boolean removed = meals.remove(meal);
        if (removed) {
            notifyObservers();
        }
        return removed;
    }

    /** Removes the meal assigned to a day and meal type. Returns true if one was removed. */
    public boolean removeMeal(DayOfWeek day, PlannedMeal.MealType mealType) {
        PlannedMeal meal = getMeal(day, mealType);
        return meal != null && removeMeal(meal);
    }

    /** The meal in a slot, or {@code null} when the slot is empty. */
    public PlannedMeal getMeal(DayOfWeek day, PlannedMeal.MealType mealType) {
        Objects.requireNonNull(day, "Day cannot be null");
        Objects.requireNonNull(mealType, "Meal type cannot be null");
        for (PlannedMeal meal : meals) {
            if (meal.isSlot(day, mealType)) {
                return meal;
            }
        }
        return null;
    }

    /** Unmodifiable snapshot of the meals, sorted by day then meal type. */
    public List<PlannedMeal> getMeals() {
        List<PlannedMeal> copy = new ArrayList<>(meals);
        copy.sort(Comparator.comparing(PlannedMeal::getDay).thenComparing(PlannedMeal::getMealType));
        return List.copyOf(copy);
    }

    public int getScheduledMealCount() {
        return meals.size();
    }

    /** Sum of the approximate costs of all meals. */
    public double getEstimatedCost() {
        double total = 0;
        for (PlannedMeal m : meals) {
            total += m.getEstimatedCost();
        }
        return total;
    }

    /** Multi-line description used by the GUI and CLI. */
    public String describe() {
        if (meals.isEmpty()) {
            return "(the meal plan is empty)";
        }
        StringBuilder sb = new StringBuilder("Meal plan for week starting " + weekStart + "\n");
        DayOfWeek current = null;
        for (PlannedMeal m : getMeals()) {
            if (m.getDay() != current) {
                current = m.getDay();
                sb.append(current).append('\n');
            }
            sb.append(String.format("  %-9s %s [%s] x%d  ~%s%n", m.getMealType(), m.getRecipe().getName(),
                m.getRecipe().getId(), m.getServings(), Quantities.money(m.getEstimatedCost())));
        }
        sb.append("Estimated total: ").append(Quantities.money(getEstimatedCost()));
        return sb.toString();
    }
}
