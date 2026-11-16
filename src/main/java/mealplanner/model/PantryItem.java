package mealplanner.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One ingredient or leftover stored in the user's pantry (Stage 1 F02).
 *
 * <p>Stores quantity, unit, optional expiry date, kind and a storage note,
 * and answers expiry questions relative to a given date.</p>
 */
public class PantryItem {

    /** Kind of pantry entry. LEFTOVER identifies previously prepared food. */
    public enum ItemKind {
        INGREDIENT,
        LEFTOVER
    }

    private final String ingredientName;
    private double amount;
    private final String unit;
    private LocalDate expiryDate;
    private ItemKind kind;
    private String storageNote;

    /**
     * @param ingredientName non-blank name
     * @param amount         non-negative finite quantity
     * @param unit           non-blank unit
     * @param expiryDate     optional expiry date; {@code null} means unknown and is never invented
     * @param kind           ingredient or leftover
     * @param storageNote    optional free-text note (fridge, freezer...)
     */
    public PantryItem(String ingredientName, double amount, String unit,
                      LocalDate expiryDate, ItemKind kind, String storageNote) {
        if (ingredientName == null || ingredientName.isBlank()) {
            throw new IllegalArgumentException("Ingredient name cannot be empty");
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("Unit cannot be empty");
        }
        if (kind == null) {
            throw new IllegalArgumentException("Item kind cannot be null");
        }
        this.ingredientName = ingredientName.trim();
        this.unit = unit.trim();
        this.expiryDate = expiryDate;
        this.kind = kind;
        this.storageNote = storageNote == null ? "" : storageNote.trim();
        setAmount(amount);
    }

    /** Updates the quantity, rejecting negative, infinite and NaN values. */
    public void setAmount(double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Amount must be a non-negative finite number");
        }
        this.amount = amount;
    }

    /**
     * True if the item expires within {@code days} days of today, inclusive.
     * Items without an expiry date and already-expired items return false.
     */
    public boolean expiresWithin(int days) {
        return expiresWithin(days, LocalDate.now());
    }

    /** Same as {@link #expiresWithin(int)} but relative to {@code today} (testable). */
    public boolean expiresWithin(int days, LocalDate today) {
        if (days < 0) {
            throw new IllegalArgumentException("Days cannot be negative");
        }
        if (expiryDate == null) {
            return false;
        }
        return !expiryDate.isBefore(today) && !expiryDate.isAfter(today.plusDays(days));
    }

    /** True if the expiry date is strictly before {@code today}. Items expiring today are usable. */
    public boolean isExpired(LocalDate today) {
        return expiryDate != null && expiryDate.isBefore(today);
    }

    public boolean isExpired() {
        return isExpired(LocalDate.now());
    }

    /** True when the item is marked as leftover. This does not guarantee it is safe to eat. */
    public boolean isLeftover() {
        return kind == ItemKind.LEFTOVER;
    }

    public String getIngredientName() {
        return ingredientName;
    }

    public double getAmount() {
        return amount;
    }

    public String getUnit() {
        return unit;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public ItemKind getKind() {
        return kind;
    }

    public String getStorageNote() {
        return storageNote;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public void setKind(ItemKind kind) {
        this.kind = Objects.requireNonNull(kind, "Item kind cannot be null");
    }

    public void setStorageNote(String storageNote) {
        this.storageNote = storageNote == null ? "" : storageNote.trim();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Quantities.format(amount)).append(' ').append(unit).append(' ').append(ingredientName);
        if (kind == ItemKind.LEFTOVER) {
            sb.append(" [leftover]");
        }
        sb.append(expiryDate == null ? " (no expiry date)" : " (expires " + expiryDate + ")");
        if (!storageNote.isEmpty()) {
            sb.append(" - ").append(storageNote);
        }
        return sb.toString();
    }
}
