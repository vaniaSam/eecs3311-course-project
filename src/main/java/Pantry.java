
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages the user's pantry inventory.
 *
 * Supports F02: Pantry and Expiry Management.
 * Stores pantry items and provides methods to add,
 * remove, find and update available ingredients.
 */
public class Pantry {

    // Stores the pantry items in their insertion order.
    private final List<PantryItem> items = new ArrayList<>();

    /**
     * Adds an ingredient or leftover to the pantry.
     *
     * Separate entries are allowed for the same ingredient
     * because they may have different expiry dates or units.
     */
    public void addItem(PantryItem item) {
        items.add(Objects.requireNonNull(
            item, "Pantry item cannot be null"
        ));
    }

    /**
     * Removes an existing pantry item.
     *
     * Returns true if the item was found and removed.
     */
    public boolean removeItem(PantryItem item) {
        return items.remove(item);
    }

    /**
     * Finds all pantry entries matching an ingredient name.
     * The search ignores capitalization.
     */
    public List<PantryItem> findItems(String ingredientName) {
        if (ingredientName == null ||
            ingredientName.isBlank()) {
            throw new IllegalArgumentException(
                "Ingredient name cannot be empty"
            );
        }

        List<PantryItem> matches = new ArrayList<>();

        for (PantryItem item : items) {
            if (item.getIngredientName().equalsIgnoreCase(
                    ingredientName.trim())) {
                matches.add(item);
            }
        }

        return matches;
    }

    /**
     * Calculates the available quantity of an ingredient
     * in a specific unit.
     *
     * Units are compared rather than converted to prevent
     * incorrect totals, such as adding grams to millilitres.
     */
    public double getAvailableQuantity(
            String ingredientName, String unit) {

        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException(
                "Unit cannot be empty"
            );
        }

        double total = 0;

        for (PantryItem item : findItems(ingredientName)) {
            if (item.getUnit().equalsIgnoreCase(unit.trim())) {
                total += item.getAmount();
            }
        }

        return total;
    }

    /**
     * Updates the amount of an item already in the pantry.
     *
     * Returns false if the item does not exist.
     */
    public boolean updateQuantity(
            PantryItem item, double newAmount) {

        if (!items.contains(item)) {
            return false;
        }

        // PantryItem performs quantity validation.
        item.setAmount(newAmount);
        return true;
    }

    /**
     * Returns a copy of the inventory list so callers
     * cannot add or remove entries directly.
     */
    public List<PantryItem> getItems() {
        return new ArrayList<>(items);
    }

    /**
     * Returns the number of pantry entries.
     */
    public int size() {
        return items.size();
    }
}
