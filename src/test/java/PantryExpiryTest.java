
import java.time.LocalDate;

/**
 * Tests expired and soon-to-expire pantry items.
 */
public class PantryExpiryTest {

    public static void main(String[] args) {
        Pantry pantry = new Pantry();

        PantryItem expired = new PantryItem(
            "Milk", 1.0, "L",
            LocalDate.now().minusDays(2),
            PantryItem.ItemKind.INGREDIENT,
            "Refrigerate"
        );

        PantryItem expiringSoon = new PantryItem(
            "Eggs", 6.0, "pieces",
            LocalDate.now().plusDays(2),
            PantryItem.ItemKind.INGREDIENT,
            "Refrigerate"
        );

        PantryItem fresh = new PantryItem(
            "Rice", 2.0, "kg",
            LocalDate.now().plusDays(30),
            PantryItem.ItemKind.INGREDIENT,
            "Store in pantry"
        );

        pantry.addItem(expired);
        pantry.addItem(expiringSoon);
        pantry.addItem(fresh);

        if (!pantry.getExpiredItems().contains(expired)
                || pantry.getExpiredItems().size() != 1) {
            throw new AssertionError("Expired item detection failed");
        }

        if (!pantry.getExpiringSoonItems(3).contains(expiringSoon)
                || pantry.getExpiringSoonItems(3).size() != 1) {
            throw new AssertionError("Expiring soon detection failed");
        }

        if (!pantry.getExpiringSoonItems(0).isEmpty()) {
            throw new AssertionError("Zero-day expiry check failed");
        }

        // Expired ingredients must not count as available.
        PantryItem expiredMilk = new PantryItem(
            "Milk", 3.0, "L",
            LocalDate.now().minusDays(1),
            PantryItem.ItemKind.INGREDIENT,
            "Refrigerate"
        );

        PantryItem freshMilk = new PantryItem(
            "Milk", 2.0, "L",
            LocalDate.now().plusDays(5),
            PantryItem.ItemKind.INGREDIENT,
            "Refrigerate"
        );

        pantry.addItem(expiredMilk);
        pantry.addItem(freshMilk);

        if (pantry.getAvailableQuantity("Milk", "L") != 2.0) {
            throw new AssertionError(
                "Expired ingredients should not count as available"
            );
        }


        System.out.println("Pantry expiry test passed!");
    }
}
