
import java.util.Objects;

public class IngredientRequirement {
    private final String name;
    private final double quantity;
    private final String unit;

    public IngredientRequirement(String name, double quantity, String unit) {
        if (name == null || name.isBlank()
                || unit == null || unit.isBlank()
                || !Double.isFinite(quantity) || quantity <= 0) {
            throw new IllegalArgumentException(
                "Ingredient needs a name, positive quantity and unit"
            );
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

    public IngredientRequirement scale(double factor) {
        if (!Double.isFinite(factor) || factor <= 0) {
            throw new IllegalArgumentException("Invalid scale factor");
        }
        return new IngredientRequirement(name, quantity * factor, unit);
    }
}

