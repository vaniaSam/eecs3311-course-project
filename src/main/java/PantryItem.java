import java.time.LocalDate;

import java.util.Objects;

/**

 * Represents one ingredient stored in the user's pantry.

 *

 * Supports F02: Pantry and Expiry Management.

 * Stores ingredient details and identifies leftovers

 * or items approaching their expiry date.

 */

public class PantryItem {

    /**

     * Describes the type of pantry item.

     * LEFTOVER identifies previously prepared food.

     */

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

     * Creates a pantry item with its quantity,

     * optional expiry date, type and storage note.

     */

    public PantryItem(String ingredientName,

                      double amount,

                      String unit,

                      LocalDate expiryDate,

                      ItemKind kind,

                      String storageNote) {

        if (ingredientName == null || ingredientName.isBlank()) {

            throw new IllegalArgumentException(

                "Ingredient name cannot be empty"

            );

        }

        if (unit == null || unit.isBlank()) {

            throw new IllegalArgumentException(

                "Unit cannot be empty"

            );

        }

        if (kind == null) {

            throw new IllegalArgumentException(

                "Item kind cannot be null"

            );

        }

        this.ingredientName = ingredientName.trim();

        this.unit = unit.trim();

        this.expiryDate = expiryDate;

        this.kind = kind;

        this.storageNote = storageNote == null ? "" : storageNote;

        setAmount(amount);

    }

    /**

     * Updates the quantity while rejecting negative,

     * infinite and non-numeric values.

     */

    public void setAmount(double amount) {

        if (!Double.isFinite(amount) || amount < 0) {

            throw new IllegalArgumentException(

                "Amount must be a non-negative finite number"

            );

        }

        this.amount = amount;

    }

    /**

     * Checks whether an item expires within the

     * specified number of days, including today.

     *

     * Items without expiry dates return false.

     * Already expired items also return false.

     */

    public boolean expiresWithin(int days) {

        if (days < 0) {

            throw new IllegalArgumentException(

                "Days cannot be negative"

            );

        }

        if (expiryDate == null) {

            return false;

        }

        LocalDate today = LocalDate.now();

        LocalDate deadline = today.plusDays(days);

        return !expiryDate.isBefore(today)

            && !expiryDate.isAfter(deadline);

    }

    /**

     * Returns true when the item is marked as leftover.

     * This does not guarantee that the food is safe to eat.

     */

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

        this.kind = Objects.requireNonNull(

            kind, "Item kind cannot be null"

        );

    }

    public void setStorageNote(String storageNote) {

        this.storageNote =

            storageNote == null ? "" : storageNote;

    }

}