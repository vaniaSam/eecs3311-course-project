package mealplanner.model;

/**
 * One line of a {@link ShoppingList}: the missing quantity of an ingredient,
 * plus an optional note (for example when pantry stock exists in another unit
 * and could not be converted reliably).
 */
public class ShoppingListItem {
    private final String name;
    private final double quantity;
    private final String unit;
    private final String note;

    public ShoppingListItem(String name, double quantity, String unit) {
        this(name, quantity, unit, "");
    }

    public ShoppingListItem(String name, double quantity, String unit, String note) {
        if (name == null || name.isBlank()
                || unit == null || unit.isBlank()
                || !Double.isFinite(quantity) || quantity <= 0) {
            throw new IllegalArgumentException("Invalid shopping list item");
        }
        this.name = name.trim();
        this.quantity = quantity;
        this.unit = unit.trim();
        this.note = note == null ? "" : note.trim();
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

    public String getNote() {
        return note;
    }

    @Override
    public String toString() {
        return Quantities.format(quantity) + " " + unit + " " + name + (note.isEmpty() ? "" : "  (" + note + ")");
    }
}
