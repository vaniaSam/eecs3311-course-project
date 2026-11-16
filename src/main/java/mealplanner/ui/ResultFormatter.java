package mealplanner.ui;

import java.time.LocalDate;
import java.util.List;

import mealplanner.agent.AgentTrace;
import mealplanner.agent.AssistantReply;
import mealplanner.agent.LeftoverPlanResult;
import mealplanner.agent.PlanGenerationResult;
import mealplanner.agent.Recommendation;
import mealplanner.agent.RecipeGenerationResult;
import mealplanner.agent.RecommendationResult;
import mealplanner.agent.Substitution;
import mealplanner.agent.SubstitutionResult;
import mealplanner.controller.PlanEditResult;
import mealplanner.model.PantryItem;
import mealplanner.model.Recipe;
import mealplanner.model.ValidationResult;
import mealplanner.service.RecipeMatch;

/**
 * Converts results into display text. Shared by the GUI and CLI views so both
 * present identical information (MVC: presentation logic stays in the view
 * layer, not in the model or controller).
 */
public final class ResultFormatter {

    private ResultFormatter() {
    }

    public static String pantry(List<PantryItem> items) {
        if (items.isEmpty()) {
            return "(no items)";
        }
        StringBuilder sb = new StringBuilder();
        LocalDate today = LocalDate.now();
        for (PantryItem i : items) {
            sb.append("- ").append(i);
            if (i.isExpired(today)) {
                sb.append("  [EXPIRED - not counted]");
            } else if (i.expiresWithin(3, today)) {
                sb.append("  [expires soon]");
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    public static String recipes(List<Recipe> recipes) {
        if (recipes.isEmpty()) {
            return "No recipes match. Try different keywords.";
        }
        StringBuilder sb = new StringBuilder();
        for (Recipe r : recipes) {
            sb.append(String.format("%-14s %-28s %3d min  %s/serving  %s%n", r.getId(), r.getName(),
                r.getCookingTimeMinutes(), mealplanner.model.Quantities.money(r.getCostPerServing()),
                String.join(",", r.getTags())));
        }
        return sb.toString().trim();
    }

    public static String matches(List<RecipeMatch> matches) {
        if (matches.isEmpty()) {
            return "No recipes use those ingredients.";
        }
        StringBuilder sb = new StringBuilder();
        for (RecipeMatch m : matches) {
            sb.append("- ").append(m.summary()).append('\n');
        }
        return sb.toString().trim();
    }

    public static String recommendations(RecommendationResult r, boolean showTrace) {
        StringBuilder sb = new StringBuilder("Recommendations (" + r.strategyName() + ", "
            + (r.aiUsed() ? "AI-ranked and explained" : "strategy ranking only") + ")\n");
        int n = 1;
        for (Recommendation rec : r.recommendations()) {
            sb.append(n++).append(". ").append(rec.recipe().getName()).append(" [").append(rec.recipe().getId())
                .append("]\n   ").append(rec.reason()).append('\n');
        }
        if (r.recommendations().isEmpty()) {
            sb.append("(none)\n");
        }
        r.notes().forEach(note -> sb.append("Note: ").append(note).append('\n'));
        return withTrace(sb, r.trace(), showTrace);
    }

    public static String recipeGeneration(RecipeGenerationResult r, boolean showTrace) {
        StringBuilder sb = new StringBuilder(r.message()).append('\n');
        if (r.success()) {
            sb.append('\n').append(r.recipe().describe());
            appendValidation(sb, r.validation());
        }
        return withTrace(sb, r.trace(), showTrace);
    }

    public static String plan(PlanGenerationResult r, boolean showTrace) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.accepted() ? "Plan ACCEPTED" : "Plan NOT accepted - the current plan was not changed")
            .append(r.aiUsed() ? " (assembled by the AI agent)" : " (assembled deterministically)").append('\n');
        if (!r.rationale().isBlank()) {
            sb.append("Rationale: ").append(r.rationale()).append('\n');
        }
        if (r.plan() != null) {
            sb.append('\n').append(r.plan().describe()).append('\n');
        }
        appendValidation(sb, r.validation());
        return withTrace(sb, r.trace(), showTrace);
    }

    public static String substitutions(SubstitutionResult r, boolean showTrace) {
        StringBuilder sb = new StringBuilder("Substitutes for " + r.ingredient()
            + (r.aiUsed() ? " (AI + curated)" : " (curated)") + ":\n");
        for (Substitution s : r.substitutions()) {
            sb.append("- ").append(s.describe()).append('\n');
        }
        sb.append(r.message()).append('\n');
        return withTrace(sb, r.trace(), showTrace);
    }

    public static String leftovers(LeftoverPlanResult r, boolean showTrace) {
        StringBuilder sb = new StringBuilder();
        if (r.recipe() == null) {
            sb.append(r.reason()).append('\n');
        } else {
            sb.append(r.generated() ? "New recipe created for your leftovers" : "Suggested recipe for your leftovers")
                .append(r.aiUsed() ? " (AI decision)" : " (deterministic match)").append('\n')
                .append("Uses leftovers: ").append(String.join(", ", r.leftoversUsed())).append('\n')
                .append("Why: ").append(r.reason()).append("\n\n").append(r.recipe().describe());
        }
        return withTrace(sb, r.trace(), showTrace);
    }

    public static String edit(PlanEditResult r, boolean showTrace) {
        StringBuilder sb = new StringBuilder(r.applied() ? "Applied: " : "No change: ").append(r.message())
            .append('\n');
        if (r.interpretation() != null) {
            sb.append("Interpreted by: ").append(r.interpretation().aiUsed() ? "AI agent" : "rule-based parser")
                .append('\n');
        }
        if (r.plan() != null) {
            sb.append('\n').append(r.plan().describe()).append('\n');
        }
        return r.interpretation() == null ? sb.toString().trim()
            : withTrace(sb, r.interpretation().trace(), showTrace);
    }

    public static String assistant(AssistantReply r, boolean showTrace) {
        StringBuilder sb = new StringBuilder(r.answer()).append('\n');
        if (!r.toolCalls().isEmpty()) {
            sb.append("(tools used: ").append(String.join(", ", r.toolCalls())).append(")\n");
        }
        return withTrace(sb, r.trace(), showTrace);
    }

    private static void appendValidation(StringBuilder sb, ValidationResult v) {
        v.getErrors().forEach(e -> sb.append("ERROR: ").append(e).append('\n'));
        v.getWarnings().forEach(w -> sb.append("Warning: ").append(w).append('\n'));
    }

    private static String withTrace(StringBuilder sb, AgentTrace trace, boolean show) {
        if (show && !trace.getSteps().isEmpty()) {
            sb.append("\nAgent steps:\n").append(trace.describe()).append('\n');
        }
        return sb.toString().trim();
    }
}
