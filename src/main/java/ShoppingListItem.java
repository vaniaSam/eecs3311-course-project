
public class ShoppingListItem {
    private final String name;
    private final double quantity;
    private final String unit;

    public ShoppingListItem(String name, double quantity, String unit) {
        if (name == null || name.isBlank()
                || unit == null || unit.isBlank()
                || !Double.isFinite(quantity) || quantity <= 0) {
            throw new IllegalArgumentException(
                "Invalid shopping list item");
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
}
