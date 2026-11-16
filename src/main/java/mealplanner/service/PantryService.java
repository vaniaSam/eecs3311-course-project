package mealplanner.service;

import java.util.ArrayList;
import java.util.List;

import mealplanner.model.Pantry;
import mealplanner.model.PantryItem;

/**
 * Inventory operations (Stage 1 PantryService, F02 and F09 support).
 * Wraps the {@link Pantry} domain object so the facade has one entry point
 * for pantry management.
 */
public class PantryService {

    private final Pantry pantry;

    public PantryService(Pantry pantry) {
        if (pantry == null) {
            throw new IllegalArgumentException("Pantry is required");
        }
        this.pantry = pantry;
    }

    /** Adds the item or merges it with an identical entry. */
    public PantryItem addOrUpdate(PantryItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Pantry item is required");
        }
        return pantry.addOrUpdate(item);
    }

    /** Removes all entries for an ingredient; returns how many were removed. */
    public int remove(String ingredientName) {
        return pantry.removeAll(ingredientName);
    }

    /** Non-expired items that expire within {@code days} days (inclusive). */
    public List<PantryItem> expiringWithin(int days) {
        return pantry.getExpiringSoonItems(days);
    }

    /** Already-expired items, which are never treated as available stock. */
    public List<PantryItem> expired() {
        return pantry.getExpiredItems();
    }

    /**
     * Leftover entries that are not expired and still have a positive amount.
     * Expired leftovers are excluded so they are never presumed edible.
     */
    public List<PantryItem> findLeftovers() {
        List<PantryItem> result = new ArrayList<>();
        for (PantryItem item : pantry.getItems()) {
            if (item.isLeftover() && !item.isExpired() && item.getAmount() > 0) {
                result.add(item);
            }
        }
        return result;
    }

    public Pantry getPantry() {
        return pantry;
    }
}
