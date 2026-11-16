package mealplanner.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import mealplanner.model.Pantry;
import mealplanner.model.PantryItem;
import mealplanner.model.PantryItem.ItemKind;
import mealplanner.model.UserProfile;

/**
 * Safe demonstration data: a starting profile and a pantry whose expiry dates
 * are relative to today, so expiry and leftover features can be shown on any
 * day. Users can clear and replace everything from the GUI or CLI.
 */
public final class SampleData {

    private SampleData() {
    }

    /** A default profile with a $80 weekly budget and no restrictions. */
    public static UserProfile profile() {
        UserProfile p = new UserProfile(80);
        p.updatePreferences(List.of("quick meals", "asian"));
        p.setDislikedIngredients(Set.of());
        p.setRestrictions(Set.of());
        return p;
    }

    /** A pantry with staples, items expiring soon, leftovers and one expired item. */
    public static Pantry pantry() {
        LocalDate today = LocalDate.now();
        Pantry pantry = new Pantry();
        pantry.addItem(new PantryItem("rice", 1000, "g", null, ItemKind.INGREDIENT, "cupboard"));
        pantry.addItem(new PantryItem("pasta", 500, "g", null, ItemKind.INGREDIENT, "cupboard"));
        pantry.addItem(new PantryItem("canned tomatoes", 800, "g", today.plusDays(200), ItemKind.INGREDIENT, ""));
        pantry.addItem(new PantryItem("onion", 4, "pcs", today.plusDays(20), ItemKind.INGREDIENT, ""));
        pantry.addItem(new PantryItem("garlic", 6, "pcs", today.plusDays(30), ItemKind.INGREDIENT, ""));
        pantry.addItem(new PantryItem("olive oil", 20, "tbsp", null, ItemKind.INGREDIENT, ""));
        pantry.addItem(new PantryItem("soy sauce", 10, "tbsp", null, ItemKind.INGREDIENT, ""));
        pantry.addItem(new PantryItem("egg", 6, "pcs", today.plusDays(10), ItemKind.INGREDIENT, "fridge"));
        pantry.addItem(new PantryItem("milk", 1000, "ml", today.plusDays(3), ItemKind.INGREDIENT, "fridge"));
        pantry.addItem(new PantryItem("spinach", 150, "g", today.plusDays(2), ItemKind.INGREDIENT, "fridge"));
        pantry.addItem(new PantryItem("carrot", 3, "pcs", today.plusDays(7), ItemKind.INGREDIENT, "fridge"));
        pantry.addItem(new PantryItem("chicken breast", 300, "g", today.plusDays(1), ItemKind.INGREDIENT, "fridge"));
        pantry.addItem(new PantryItem("banana", 2, "pcs", today.plusDays(4), ItemKind.INGREDIENT, ""));
        pantry.addItem(new PantryItem("oats", 500, "g", null, ItemKind.INGREDIENT, "cupboard"));
        pantry.addItem(new PantryItem("cooked rice", 400, "g", today.plusDays(2), ItemKind.LEFTOVER, "fridge, from Sunday"));
        pantry.addItem(new PantryItem("roast chicken", 250, "g", today.plusDays(1), ItemKind.LEFTOVER, "fridge"));
        pantry.addItem(new PantryItem("yogurt", 500, "g", today.minusDays(1), ItemKind.INGREDIENT, "fridge"));
        return pantry;
    }
}
