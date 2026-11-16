package mealplanner.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Keyword knowledge used by {@link PlanValidator} to check recipes against
 * dietary restrictions. Known restrictions map to lists of forbidden
 * ingredient keywords (with exceptions such as "peanut butter" for
 * dairy-free). Unknown restrictions such as "no mushrooms" are treated as a
 * custom ingredient to avoid.
 *
 * <p>This is a best-effort check on structured ingredient names only. It is
 * not a medical or allergy-safety guarantee, and the UI says so.</p>
 */
public final class DietaryRules {

    private static final List<String> MEAT = List.of("chicken", "beef", "pork", "bacon", "ham", "turkey", "lamb",
        "sausage", "meat", "veal", "duck", "gelatin", "pepperoni", "salami");
    private static final List<String> SEAFOOD = List.of("salmon", "tuna", "fish", "shrimp", "prawn", "anchovy",
        "cod", "crab", "lobster", "sardine", "tilapia", "mussel", "clam", "oyster", "scallop");
    private static final List<String> DAIRY = List.of("milk", "cheese", "butter", "yogurt", "yoghurt", "cream",
        "feta", "parmesan", "cheddar", "mozzarella", "whey", "ghee", "ricotta");
    private static final List<String> DAIRY_EXCEPTIONS = List.of("coconut milk", "almond milk", "oat milk",
        "soy milk", "peanut butter", "almond butter", "vegan butter", "dairy-free", "cocoa butter", "coconut cream");

    private static final Map<String, Rule> RULES = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        RULES.put("vegetarian", new Rule(concat(MEAT, SEAFOOD), List.of()));
        RULES.put("pescatarian", new Rule(MEAT, List.of()));
        RULES.put("vegan", new Rule(concat(concat(MEAT, SEAFOOD), concat(DAIRY, List.of("egg", "honey",
            "mayonnaise"))), DAIRY_EXCEPTIONS));
        RULES.put("dairy-free", new Rule(DAIRY, DAIRY_EXCEPTIONS));
        RULES.put("gluten-free", new Rule(List.of("flour", "bread", "pasta", "spaghetti", "noodle", "wheat",
            "barley", "rye", "tortilla", "soy sauce", "couscous", "breadcrumb", "cracker", "bun", "pita"),
            List.of("rice flour", "almond flour", "corn tortilla", "gluten-free", "tamari", "rice noodle")));
        RULES.put("nut-free", new Rule(List.of("peanut", "almond", "walnut", "cashew", "pecan", "hazelnut",
            "pistachio", "macadamia", "nut"), List.of("coconut", "nutmeg", "butternut")));
        RULES.put("egg-free", new Rule(List.of("egg", "mayonnaise"), List.of("eggplant")));
        RULES.put("halal", new Rule(List.of("pork", "bacon", "ham", "wine", "beer", "gelatin", "pepperoni",
            "salami"), List.of()));
        RULES.put("shellfish-free", new Rule(List.of("shrimp", "prawn", "crab", "lobster", "mussel", "clam",
            "oyster", "scallop"), List.of()));

        ALIASES.put("veggie", "vegetarian");
        ALIASES.put("plant-based", "vegan");
        ALIASES.put("lactose-free", "dairy-free");
        ALIASES.put("lactose intolerant", "dairy-free");
        ALIASES.put("no dairy", "dairy-free");
        ALIASES.put("celiac", "gluten-free");
        ALIASES.put("no gluten", "gluten-free");
        ALIASES.put("nut allergy", "nut-free");
        ALIASES.put("no nuts", "nut-free");
        ALIASES.put("peanut allergy", "nut-free");
        ALIASES.put("no eggs", "egg-free");
        ALIASES.put("no pork", "halal");
        ALIASES.put("no meat", "vegetarian");
    }

    private DietaryRules() {
    }

    /** Normalises spelling variants: "Gluten Free" -> "gluten-free", "nut allergy" -> "nut-free". */
    public static String normalise(String restriction) {
        String r = restriction.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String hyphen = r.replace(' ', '-');
        if (RULES.containsKey(hyphen)) {
            return hyphen;
        }
        if (ALIASES.containsKey(r)) {
            return ALIASES.get(r);
        }
        return r;
    }

    /** True if the restriction has built-in keyword knowledge. */
    public static boolean isKnown(String restriction) {
        return RULES.containsKey(normalise(restriction));
    }

    /** Names of the built-in restrictions. */
    public static List<String> knownRestrictions() {
        return new ArrayList<>(RULES.keySet());
    }

    /** True if the ingredient name breaks the restriction. */
    public static boolean violates(String restriction, String ingredientName) {
        String ingredient = ingredientName.toLowerCase(Locale.ROOT);
        String key = normalise(restriction);
        Rule rule = RULES.get(key);
        if (rule == null) {
            return containsWord(ingredient, customKeyword(key));
        }
        for (String exception : rule.exceptions) {
            if (ingredient.contains(exception)) {
                return false;
            }
        }
        for (String keyword : rule.forbidden) {
            if (containsWord(ingredient, keyword)) {
                return true;
            }
        }
        return false;
    }

    /** "no mushrooms" / "mushroom-free" / "mushroom allergy" -> "mushroom". */
    static String customKeyword(String restriction) {
        String k = restriction.replaceFirst("^(no|without|avoid)\\s+", "")
            .replaceFirst("[- ](free|allergy|allergic)$", "").trim();
        if (k.endsWith("oes")) {
            return k.substring(0, k.length() - 2);
        }
        if (k.endsWith("s") && !k.endsWith("ss")) {
            return k.substring(0, k.length() - 1);
        }
        return k;
    }

    /** True if {@code keyword} (or its plural) appears as a whole word in {@code text}. */
    static boolean containsWord(String text, String keyword) {
        if (keyword.isBlank()) {
            return false;
        }
        Pattern p = Pattern.compile("\\b" + Pattern.quote(keyword) + "(s|es)?\\b");
        return p.matcher(text).find();
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private record Rule(List<String> forbidden, List<String> exceptions) {
    }
}
