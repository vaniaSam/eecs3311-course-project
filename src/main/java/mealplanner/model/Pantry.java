package mealplanner.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The user's pantry inventory (Stage 1 F02).
 *
 * <p>Separate entries are allowed for the same ingredient because they may
 * have different expiry dates, units or kinds. Expired items are kept so the
 * user can see them, but they never count as available stock.</p>
 */
public class Pantry {

    private final List<PantryItem> items = new ArrayList<>();

    /** Adds an entry without merging. */
    public void addItem(PantryItem item) {
        items.add(Objects.requireNonNull(item, "Pantry item cannot be null"));
    }

    /**
     * Adds an item, or merges it into an existing entry with the same name,
     * unit, kind and expiry date (quantities are summed).
     *
     * @return the entry now holding the stock
     */
    public PantryItem addOrUpdate(PantryItem item) {
        Objects.requireNonNull(item, "Pantry item cannot be null");
        for (PantryItem existing : items) {
            if (existing.getIngredientName().equalsIgnoreCase(item.getIngredientName())
                    && existing.getUnit().equalsIgnoreCase(item.getUnit())
                    && existing.getKind() == item.getKind()
                    && Objects.equals(existing.getExpiryDate(), item.getExpiryDate())) {
                existing.setAmount(existing.getAmount() + item.getAmount());
                if (!item.getStorageNote().isEmpty()) {
                    existing.setStorageNote(item.getStorageNote());
                }
                return existing;
            }
        }
        items.add(item);
        return item;
    }

    /** Removes the given entry. Returns true if it was present. */
    public boolean removeItem(PantryItem item) {
        return items.remove(item);
    }

    /** Removes every entry for an ingredient name. Returns the number removed. */
    public int removeAll(String ingredientName) {
        List<PantryItem> matches = findItems(ingredientName);
        items.removeAll(matches);
        return matches.size();
    }

    /** All entries matching an ingredient name, ignoring case. */
    public List<PantryItem> findItems(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            throw new IllegalArgumentException("Ingredient name cannot be empty");
        }
        List<PantryItem> matches = new ArrayList<>();
        for (PantryItem item : items) {
            if (item.getIngredientName().equalsIgnoreCase(ingredientName.trim())) {
                matches.add(item);
            }
        }
        return matches;
    }

    /**
     * Non-expired quantity of an ingredient in a specific unit. Units are
     * compared rather than converted to avoid incorrect totals.
     */
    public double getAvailableQuantity(String ingredientName, String unit) {
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("Unit cannot be empty");
        }
        LocalDate today = LocalDate.now();
        double total = 0;
        for (PantryItem item : findItems(ingredientName)) {
            if (item.getUnit().equalsIgnoreCase(unit.trim()) && !item.isExpired(today)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /** True if any non-expired entry with a positive amount exists for the ingredient, in any unit. */
    public boolean hasUsable(String ingredientName) {
        LocalDate today = LocalDate.now();
        for (PantryItem item : findItems(ingredientName)) {
            if (item.getAmount() > 0 && !item.isExpired(today)) {
                return true;
            }
        }
        return false;
    }

    /** Updates the amount of an existing entry. Returns false if the entry is unknown. */
    public boolean updateQuantity(PantryItem item, double newAmount) {
        if (!items.contains(item)) {
            return false;
        }
        item.setAmount(newAmount);
        return true;
    }

    /** Defensive copy of the inventory. */
    public List<PantryItem> getItems() {
        return new ArrayList<>(items);
    }

    public int size() {
        return items.size();
    }

    /** Items whose expiry date is before today. Items expiring today are not expired. */
    public List<PantryItem> getExpiredItems() {
        LocalDate today = LocalDate.now();
        List<PantryItem> expired = new ArrayList<>();
        for (PantryItem item : items) {
            if (item.isExpired(today)) {
                expired.add(item);
            }
        }
        return expired;
    }

    /** Non-expired items expiring within {@code days} days, including today. */
    public List<PantryItem> getExpiringSoonItems(int days) {
        if (days < 0) {
            throw new IllegalArgumentException("Number of days cannot be negative");
        }
        LocalDate today = LocalDate.now();
        List<PantryItem> soon = new ArrayList<>();
        for (PantryItem item : items) {
            if (item.expiresWithin(days, today)) {
                soon.add(item);
            }
        }
        return soon;
    }
}
