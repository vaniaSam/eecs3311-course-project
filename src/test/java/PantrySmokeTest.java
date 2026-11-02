
import java.time.LocalDate;

/**
 * Basic smoke test for the Pantry class.
 *
 * Verifies that pantry items can be added,
 * retrieved, updated, and removed correctly.
 */
public class PantrySmokeTest {

    public static void main(String[] args) {

        // Create an empty pantry.
        Pantry pantry = new Pantry();

        // Create a sample ingredient with an expiry date.
        PantryItem milk = new PantryItem(
            "Milk",
            2.0,
            "L",
            LocalDate.now().plusDays(3),
            PantryItem.ItemKind.INGREDIENT,
            "Refrigerate"
        );

        // Test 1: Add an ingredient to the pantry.
        pantry.addItem(milk);

        if (pantry.size() != 1) {
            throw new AssertionError("Item was not added");
        }

        // Test 2: Check the available quantity.
        if (pantry.getAvailableQuantity("milk", "L") != 2.0) {
            throw new AssertionError("Incorrect quantity");
        }

        // Test 3: Update the ingredient quantity.
        pantry.updateQuantity(milk, 1.5);

        if (pantry.getAvailableQuantity("Milk", "L") != 1.5) {
            throw new AssertionError("Quantity update failed");
        }

        // Test 4: Remove the ingredient.
        if (!pantry.removeItem(milk) || pantry.size() != 0) {
            throw new AssertionError("Item removal failed");
        }

        // All basic pantry operations succeeded.
        System.out.println("Pantry smoke test passed!");
    }
}
