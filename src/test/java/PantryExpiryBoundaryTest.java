
import java.time.LocalDate;

/**
 * Tests boundary cases and invalid inputs for pantry expiry tracking.
 *
 * Supports F02: Pantry and Expiry Management.
 */
public class PantryExpiryBoundaryTest {

    public static void main(String[] args) {
        Pantry pantry = new Pantry();
        LocalDate today = LocalDate.now();

        // An item expiring yesterday is already expired.
        PantryItem yesterday = new PantryItem(
            "Yogurt", 1.0, "cup",
            today.minusDays(1),
            PantryItem.ItemKind.INGREDIENT,
            "Refrigerate"
        );

        // An item expiring today is not yet considered expired.
        PantryItem todayItem = new PantryItem(
            "Bread", 1.0, "loaf",
            today,
            PantryItem.ItemKind.INGREDIENT,
            "Store sealed"
        );

        // An item exactly seven days away is within the window.
        PantryItem sevenDays = new PantryItem(
            "Cheese", 1.0, "pack",
            today.plusDays(7),
            PantryItem.ItemKind.INGREDIENT,
            "Refrigerate"
        );

        // An item eight days away is outside the window.
        PantryItem eightDays = new PantryItem(
            "Pasta", 1.0, "pack",
            today.plusDays(8),
            PantryItem.ItemKind.INGREDIENT,
            "Store dry"
        );

        pantry.addItem(yesterday);
        pantry.addItem(todayItem);
        pantry.addItem(sevenDays);
        pantry.addItem(eightDays);

        // Verify that only yesterday's item has expired.
        if (pantry.getExpiredItems().size() != 1
                || !pantry.getExpiredItems().contains(yesterday)) {
            throw new AssertionError("Expired date boundary failed");
        }

        // Verify that the seven-day window includes its endpoints.
        if (pantry.getExpiringSoonItems(7).size() != 2
                || !pantry.getExpiringSoonItems(7).contains(todayItem)
                || !pantry.getExpiringSoonItems(7).contains(sevenDays)) {
            throw new AssertionError("Seven-day expiry boundary failed");
        }

        // A zero-day window should include only today's item.
        if (pantry.getExpiringSoonItems(0).size() != 1
                || !pantry.getExpiringSoonItems(0).contains(todayItem)) {
            throw new AssertionError("Zero-day expiry boundary failed");
        }

        // Invalid negative windows must be rejected.
        boolean rejectedNegativeDays = false;

        try {
            pantry.getExpiringSoonItems(-1);
        } catch (IllegalArgumentException e) {
            rejectedNegativeDays = true;
        }

        if (!rejectedNegativeDays) {
            throw new AssertionError("Negative days were not rejected");
        }

        // Expired inventory should not count toward usable quantity.
        if (pantry.getAvailableQuantity("Yogurt", "cup") != 0.0) {
            throw new AssertionError("Expired inventory was counted");
        }

        System.out.println("Pantry expiry boundary test passed!");
    }
}
