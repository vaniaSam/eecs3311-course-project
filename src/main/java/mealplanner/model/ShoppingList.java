package mealplanner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Consolidated list of ingredients that must be bought for a meal plan (Stage 1 F10). */
public class ShoppingList {
    private final List<ShoppingListItem> items;

    public ShoppingList(List<ShoppingListItem> items) {
        if (items == null || items.stream().anyMatch(i -> i == null)) {
            throw new IllegalArgumentException("Invalid shopping list");
        }
        this.items = new ArrayList<>(items);
    }

    public List<ShoppingListItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** Multi-line description, one item per line. */
    public String describe() {
        if (items.isEmpty()) {
            return "(nothing to buy - the pantry covers every planned meal)";
        }
        StringBuilder sb = new StringBuilder();
        for (ShoppingListItem item : items) {
            sb.append("[ ] ").append(item).append('\n');
        }
        return sb.toString().trim();
    }
}
