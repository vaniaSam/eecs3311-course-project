package mealplanner.model;

import java.util.Locale;

/**
 * An ingredient amount required by a {@link Recipe}, e.g. "300 g chicken".
 *
 * <p>Immutable. Quantities are always positive and finite; units are kept as
 * free text and are compared case-insensitively but never converted, so that
 * the application never silently adds grams to millilitres (Stage 1 F10 rule).</p>
 */
public final class IngredientRequirement {
    private final String name;
    private final double quantity;
    private final String unit;

    /**
     * @param name     ingredient name, non-blank
     * @param quantity positive finite amount
     * @param unit     unit of measure, non-blank (e.g. "g", "ml", "pcs")
     * @throws IllegalArgumentException if any argument is invalid
     */
    public IngredientRequirement(String name, double quantity, String unit) {
        if (name == null || name.isBlank()
                || unit == null || unit.isBlank()
                || !Double.isFinite(quantity) || quantity <= 0) {
            throw new IllegalArgumentException(
                "Ingredient needs a name, positive quantity and unit");
        }
        this.name = name.trim();
        this.quantity = quantity;
        this.unit = unit.trim();
    }

    public String getName() {
        return name;
    }

    public double getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    /** Returns a new requirement whose quantity is multiplied by {@code factor}. */
    public IngredientRequirement scale(double factor) {
        if (!Double.isFinite(factor) || factor <= 0) {
            throw new IllegalArgumentException("Invalid scale factor");
        }
        return new IngredientRequirement(name, quantity * factor, unit);
    }

    /** Case-insensitive comparison of the ingredient name. */
    public boolean hasName(String other) {
        return other != null && name.equalsIgnoreCase(other.trim());
    }

    /** Lower-case key used when merging requirements of the same name and unit. */
    public String mergeKey() {
        return name.toLowerCase(Locale.ROOT) + "|" + unit.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return Quantities.format(quantity) + " " + unit + " " + name;
    }
}
