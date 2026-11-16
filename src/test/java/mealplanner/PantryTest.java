package mealplanner;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import mealplanner.model.Pantry;
import mealplanner.model.PantryItem;
import mealplanner.model.PantryItem.ItemKind;
import mealplanner.service.PantryService;

/** F02 Pantry and Expiry Management (ported from the earlier smoke / expiry / boundary tests). */
class PantryTest {

    private final LocalDate today = LocalDate.now();

    private PantryItem item(String name, double amount, String unit, LocalDate expiry) {
        return new PantryItem(name, amount, unit, expiry, ItemKind.INGREDIENT, "");
    }

    @Test
    void addUpdateAndRemove() {
        Pantry pantry = new Pantry();
        PantryItem milk = item("Milk", 2.0, "L", today.plusDays(3));
        pantry.addItem(milk);
        assertEquals(1, pantry.size());
        assertEquals(2.0, pantry.getAvailableQuantity("milk", "L"));
        assertTrue(pantry.updateQuantity(milk, 1.5));
        assertEquals(1.5, pantry.getAvailableQuantity("Milk", "L"));
        assertTrue(pantry.removeItem(milk));
        assertEquals(0, pantry.size());
    }

    @Test
    void addOrUpdateMergesIdenticalEntries() {
        Pantry pantry = new Pantry();
        pantry.addOrUpdate(item("Rice", 500, "g", null));
        pantry.addOrUpdate(item("rice", 250, "g", null));
        pantry.addOrUpdate(item("rice", 1, "kg", null));
        assertEquals(2, pantry.size(), "different units stay separate");
        assertEquals(750, pantry.getAvailableQuantity("RICE", "g"));
    }

    @Test
    void expiryBoundaries() {
        Pantry pantry = new Pantry();
        PantryItem yesterday = item("Yogurt", 1, "cup", today.minusDays(1));
        PantryItem todayItem = item("Bread", 1, "loaf", today);
        PantryItem sevenDays = item("Cheese", 1, "pack", today.plusDays(7));
        PantryItem eightDays = item("Pasta", 1, "pack", today.plusDays(8));
        pantry.addItem(yesterday);
        pantry.addItem(todayItem);
        pantry.addItem(sevenDays);
        pantry.addItem(eightDays);

        assertEquals(List.of(yesterday), pantry.getExpiredItems());
        assertEquals(List.of(todayItem, sevenDays), pantry.getExpiringSoonItems(7));
        assertEquals(List.of(todayItem), pantry.getExpiringSoonItems(0));
        assertThrows(IllegalArgumentException.class, () -> pantry.getExpiringSoonItems(-1));
        assertEquals(0.0, pantry.getAvailableQuantity("Yogurt", "cup"), "expired stock is not available");
    }

    @Test
    void expiredStockIsNotCountedButFreshStockIs() {
        Pantry pantry = new Pantry();
        pantry.addItem(item("Milk", 3, "L", today.minusDays(1)));
        pantry.addItem(item("Milk", 2, "L", today.plusDays(5)));
        assertEquals(2.0, pantry.getAvailableQuantity("Milk", "L"));
    }

    @Test
    void invalidInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> item("", 1, "g", null));
        assertThrows(IllegalArgumentException.class, () -> item("Salt", -1, "g", null));
        assertThrows(IllegalArgumentException.class, () -> item("Salt", Double.NaN, "g", null));
        assertThrows(IllegalArgumentException.class, () -> item("Salt", 1, " ", null));
        PantryItem salt = item("Salt", 1, "g", null);
        assertThrows(IllegalArgumentException.class, () -> salt.setAmount(-5));
        assertEquals(1, salt.getAmount(), "rejected update leaves the old value");
    }

    @Test
    void leftoversExcludeExpiredAndEmptyEntries() {
        Pantry pantry = new Pantry();
        PantryItem good = new PantryItem("cooked rice", 300, "g", today.plusDays(1), ItemKind.LEFTOVER, "");
        pantry.addItem(good);
        pantry.addItem(new PantryItem("old soup", 300, "ml", today.minusDays(2), ItemKind.LEFTOVER, ""));
        pantry.addItem(new PantryItem("pizza", 0, "slices", today.plusDays(1), ItemKind.LEFTOVER, ""));
        pantry.addItem(item("rice", 300, "g", null));
        assertEquals(List.of(good), new PantryService(pantry).findLeftovers());
    }

    @Test
    void removeAllByName() {
        PantryService service = new PantryService(new Pantry());
        service.addOrUpdate(item("egg", 6, "pcs", null));
        service.addOrUpdate(item("egg", 2, "pcs", today.plusDays(2)));
        assertEquals(2, service.remove("EGG"));
        assertEquals(0, service.getPantry().size());
    }
}
