
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
}
