package mealplanner.model;

import java.util.Locale;

/** Small formatting helper shared by the model and user interfaces. */
public final class Quantities {
    private Quantities() {
    }

    /** Formats a quantity without trailing zeros, e.g. 2.0 -> "2", 0.3333 -> "0.33". */
    public static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return String.valueOf((long) Math.rint(value));
        }
        String s = String.format(Locale.ROOT, "%.2f", value);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** Formats a currency amount as "$12.50". */
    public static String money(double value) {
        return String.format(Locale.ROOT, "$%.2f", value);
    }
}
