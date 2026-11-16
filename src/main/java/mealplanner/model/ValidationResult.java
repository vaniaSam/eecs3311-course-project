package mealplanner.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of a PlanValidator check. Errors make a proposal unacceptable;
 * warnings are shown to the user but do not block acceptance.
 */
public class ValidationResult {
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public void addError(String message) {
        errors.add(message);
    }

    public void addWarning(String message) {
        warnings.add(message);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return List.copyOf(errors);
    }

    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    /** Merges another result into this one. */
    public ValidationResult merge(ValidationResult other) {
        errors.addAll(other.errors);
        warnings.addAll(other.warnings);
        return this;
    }

    @Override
    public String toString() {
        if (errors.isEmpty() && warnings.isEmpty()) {
            return "valid";
        }
        return "errors=" + errors + ", warnings=" + warnings;
    }
}
