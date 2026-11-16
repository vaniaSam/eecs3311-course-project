package mealplanner.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small curated substitution knowledge base. The agent <em>retrieves</em>
 * matching entries and gives them to the LLM as grounding (retrieval-augmented
 * prompting); when the LLM is unavailable the curated entries are shown alone.
 */
public final class SubstitutionCatalog {

    /** A curated substitute and usage note. */
    public record Entry(String name, String note) {
    }

    private static final Map<String, List<Entry>> CATALOG = new LinkedHashMap<>();

    static {
        CATALOG.put("egg", List.of(new Entry("flax egg", "1 tbsp ground flax + 3 tbsp water per egg; for baking"),
            new Entry("mashed banana", "1/4 cup per egg; sweet, for baking"),
            new Entry("silken tofu", "1/4 cup per egg; for scrambles")));
        CATALOG.put("milk", List.of(new Entry("oat milk", "1:1"), new Entry("soy milk", "1:1"),
            new Entry("coconut milk", "1:1, richer flavour")));
        CATALOG.put("butter", List.of(new Entry("olive oil", "use 3/4 the amount"),
            new Entry("coconut oil", "1:1")));
        CATALOG.put("cheese", List.of(new Entry("nutritional yeast", "2 tbsp per 30 g, savoury flavour")));
        CATALOG.put("yogurt", List.of(new Entry("sour cream", "1:1"),
            new Entry("coconut yogurt", "1:1, dairy-free")));
        CATALOG.put("chicken", List.of(new Entry("tofu", "same weight, press first"),
            new Entry("chickpeas", "same weight, for curries and stews"), new Entry("turkey", "same weight")));
        CATALOG.put("beef", List.of(new Entry("lentils", "about half the dry weight"),
            new Entry("mushroom", "same weight, chopped"), new Entry("turkey", "same weight")));
        CATALOG.put("rice", List.of(new Entry("quinoa", "same dry weight"),
            new Entry("couscous", "same dry weight (contains gluten)")));
        CATALOG.put("pasta", List.of(new Entry("rice noodles", "same weight, gluten-free"),
            new Entry("zucchini noodles", "low-carb")));
        CATALOG.put("soy sauce", List.of(new Entry("tamari", "1:1, usually gluten-free"),
            new Entry("coconut aminos", "1:1, soy-free")));
        CATALOG.put("flour", List.of(new Entry("almond flour", "1:1 in pancakes (contains nuts)"),
            new Entry("oat flour", "1:1")));
        CATALOG.put("peanut butter", List.of(new Entry("sunflower seed butter", "1:1, nut-free"),
            new Entry("tahini", "1:1")));
        CATALOG.put("honey", List.of(new Entry("maple syrup", "1:1"), new Entry("sugar", "use 1.25x")));
        CATALOG.put("lemon", List.of(new Entry("lime", "1:1"), new Entry("vinegar", "use half")));
        CATALOG.put("onion", List.of(new Entry("shallot", "1:1"), new Entry("leek", "1:1")));
        CATALOG.put("salmon", List.of(new Entry("trout", "same weight"), new Entry("tofu", "same weight")));
        CATALOG.put("tortilla", List.of(new Entry("lettuce leaves", "as wraps, gluten-free"),
            new Entry("corn tortilla", "1:1, usually gluten-free")));
        CATALOG.put("bread", List.of(new Entry("rice cakes", "1 per slice")));
    }

    private SubstitutionCatalog() {
    }

    /** Curated entries whose key appears in the ingredient name (e.g. "chicken breast" -> chicken). */
    public static List<Entry> lookup(String ingredient) {
        if (ingredient == null) {
            return List.of();
        }
        String name = ingredient.toLowerCase(Locale.ROOT);
        String bestKey = null;
        for (String key : CATALOG.keySet()) {
            if (DietaryRules.containsWord(name, key) && (bestKey == null || key.length() > bestKey.length())) {
                bestKey = key;
            }
        }
        return bestKey == null ? List.of() : CATALOG.get(bestKey);
    }
}
