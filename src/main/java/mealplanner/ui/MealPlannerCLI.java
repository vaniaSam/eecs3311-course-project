package mealplanner.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import mealplanner.agent.RecipeGenerationResult;
import mealplanner.controller.MealPlannerController;
import mealplanner.model.MealPlan;
import mealplanner.model.PantryItem;
import mealplanner.model.PlanRequest;
import mealplanner.model.Recipe;
import mealplanner.model.UserProfile;

/**
 * <b>MVC pattern - View (command line).</b> Reads commands, passes the raw
 * text to {@link MealPlannerController} and prints results with
 * {@link ResultFormatter}. It contains no planning logic, so every feature
 * behaves exactly as in the GUI. Works interactively and with piped input.
 */
public class MealPlannerCLI {

    private static final String HELP = String.join("\n",
        "Commands (quote multi-word values, e.g. pantry add \"olive oil\" 10 tbsp):",
        "  F01 profile                              show profile",
        "      profile budget <amount>              set weekly budget (0 = no limit)",
        "      profile prefs <a, b>                 set preferences",
        "      profile dislikes <a, b>              set disliked ingredients",
        "      profile restrictions <a, b>          set dietary restrictions (vegetarian, vegan, nut-free...)",
        "  F02 pantry                               list pantry",
        "      pantry add <name> <qty> <unit> [expiry YYYY-MM-DD|+N|-] [ingredient|leftover] [note]",
        "      pantry remove <name>",
        "      pantry expiring [days]               items expiring soon (default 3)",
        "      pantry leftovers | pantry expired",
        "  F03 recipes | recipe <id>                list recipes / show one",
        "      search <keywords>                    keyword search",
        "      search-ingredients <a, b>            recipes using ingredients",
        "      search-pantry                        recipes using my pantry",
        "  F04 recommend [options]                  AI-assisted recommendations",
        "  F05 generate-recipe [options]            AI recipe generation; then: save-recipe",
        "  F06 plan [options]                       generate weekly plan; show-plan",
        "  F07 scale <id> <servings>                scale a recipe",
        "  F08 substitute <id> <ingredient>         ingredient substitutes",
        "  F09 leftovers [options]                  recipe from leftovers",
        "  F10 shopping                             shopping list for the current plan",
        "  F11 edit <instruction>                   natural-language plan edit",
        "      replace <day> <meal> <recipeId>      manual replacement;  undo | history",
        "  F12 ask <question>                       tool-using kitchen assistant; forget",
        "      status | trace on|off | help | exit",
        "Options: --priority budget|pantry --days 1-7 --meals breakfast,lunch,dinner --servings N --note \"text\"");

    private final MealPlannerController controller;
    private final BufferedReader in;
    private final PrintStream out;
    private boolean showTrace = true;
    private Recipe lastGenerated;
    private final boolean echoInput;

    /**
     * @param echoInput print each command after the prompt; useful when input is piped from a file
     */
    public MealPlannerCLI(MealPlannerController controller, InputStream in, PrintStream out, boolean echoInput) {
        this.controller = controller;
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
        this.echoInput = echoInput;
    }

    /** Runs the read-eval-print loop until "exit" or end of input. */
    public void run() {
        out.println("AI Recipe & Meal Planning Agent - command line");
        out.println(controller.llmStatus());
        out.println("Type 'help' for commands.");
        while (true) {
            out.print("\nmeal-planner> ");
            out.flush();
            String line;
            try {
                line = in.readLine();
            } catch (IOException e) {
                out.println("Input error: " + e.getMessage());
                return;
            }
            if (line == null) {
                out.println();
                return;
            }
            if (echoInput) {
                out.println(line); // makes piped/scripted sessions readable
            }
            if (!execute(line)) {
                out.println("Goodbye.");
                return;
            }
        }
    }

    /**
     * Executes one command line and prints the result.
     *
     * @return false if the user asked to exit
     */
    public boolean execute(String line) {
        List<String> args = tokenize(line);
        if (args.isEmpty() || args.get(0).startsWith("#")) {
            return true;
        }
        String cmd = args.get(0).toLowerCase(Locale.ROOT);
        List<String> rest = args.subList(1, args.size());
        try {
            switch (cmd) {
                case "help", "?" -> out.println(HELP);
                case "exit", "quit" -> {
                    return false;
                }
                case "status" -> out.println(controller.llmStatus());
                case "trace" -> {
                    showTrace = rest.isEmpty() || !rest.get(0).equalsIgnoreCase("off");
                    out.println("Agent steps " + (showTrace ? "shown" : "hidden") + ".");
                }
                case "profile" -> profile(rest);
                case "pantry" -> pantry(rest);
                case "recipes" -> out.println(ResultFormatter.recipes(controller.getAllRecipes()));
                case "recipe" -> out.println(controller.getRecipe(joined(rest, "recipe id")).describe());
                case "search" -> out.println(ResultFormatter.recipes(controller.searchRecipes(String.join(" ", rest))));
                case "search-ingredients" -> out.println(ResultFormatter.matches(
                    controller.searchByIngredients(String.join(" ", rest))));
                case "search-pantry" -> out.println(ResultFormatter.matches(controller.searchByPantry()));
                case "recommend" -> {
                    progress("Agent is ranking recipes");
                    out.println(ResultFormatter.recommendations(controller.recommendRecipes(request(rest)),
                        showTrace));
                }
                case "generate-recipe" -> {
                    progress("Agent is generating a recipe");
                    RecipeGenerationResult r = controller.generateRecipe(request(rest));
                    out.println(ResultFormatter.recipeGeneration(r, showTrace));
                    lastGenerated = r.recipe();
                    if (r.success()) {
                        out.println("Type 'save-recipe' to add it to your recipe collection.");
                    }
                }
                case "save-recipe" -> {
                    Recipe saved = controller.saveGeneratedRecipe(lastGenerated);
                    lastGenerated = null;
                    out.println("Saved as " + saved.getId() + ". Use 'recipe " + saved.getId() + "' to view it.");
                }
                case "plan" -> {
                    progress("Agent is planning the week");
                    out.println(ResultFormatter.plan(controller.generatePlan(request(rest)), showTrace));
                }
                case "show-plan" -> {
                    MealPlan plan = controller.getCurrentPlan();
                    out.println(plan == null ? "No meal plan yet. Use 'plan'." : plan.describe());
                }
                case "scale" -> {
                    require(rest, 2, "scale <recipeId> <servings>");
                    out.println(controller.scaleRecipe(rest.get(0), rest.get(1)).describe());
                }
                case "substitute" -> {
                    require(rest, 2, "substitute <recipeId> <ingredient>");
                    progress("Agent is looking for substitutes");
                    out.println(ResultFormatter.substitutions(controller.suggestSubstitutions(rest.get(0),
                        String.join(" ", rest.subList(1, rest.size()))), showTrace));
                }
                case "leftovers" -> {
                    progress("Agent is planning with leftovers");
                    out.println(ResultFormatter.leftovers(controller.planFromLeftovers(request(rest)), showTrace));
                }
                case "shopping" -> out.println("Shopping list:\n" + controller.getShoppingList().describe());
                case "edit" -> {
                    progress("Agent is interpreting the edit");
                    out.println(ResultFormatter.edit(controller.modifyPlan(joined(rest, "instruction")), showTrace));
                }
                case "replace" -> {
                    require(rest, 3, "replace <day> <meal> <recipeId>");
                    out.println(ResultFormatter.edit(controller.replaceMeal(rest.get(0), rest.get(1), rest.get(2)),
                        false));
                }
                case "undo" -> out.println(ResultFormatter.edit(controller.undoLastEdit(), false));
                case "history" -> {
                    List<String> h = controller.getFacade().editHistory();
                    out.println(h.isEmpty() ? "No edits yet." : "Most recent first:\n- " + String.join("\n- ", h));
                }
                case "ask" -> {
                    progress("Assistant is thinking");
                    out.println(ResultFormatter.assistant(controller.askAssistant(joined(rest, "question")),
                        showTrace));
                }
                case "forget" -> {
                    controller.clearAssistantMemory();
                    out.println("Assistant memory cleared.");
                }
                default -> out.println("Unknown command '" + cmd + "'. Type 'help'.");
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            out.println("Error: " + e.getMessage());
        }
        return true;
    }

    private void profile(List<String> rest) {
        UserProfile p = controller.getProfile();
        if (rest.isEmpty()) {
            out.println(p.describe());
            return;
        }
        String field = rest.get(0).toLowerCase(Locale.ROOT);
        String value = String.join(" ", rest.subList(1, rest.size()));
        String budget = String.valueOf(p.getWeeklyBudget());
        String prefs = String.join(",", p.getPreferences());
        String dislikes = String.join(",", p.getDislikedIngredients());
        String restrictions = String.join(",", p.getRestrictions());
        switch (field) {
            case "budget" -> budget = value;
            case "prefs", "preferences" -> prefs = value;
            case "dislikes" -> dislikes = value;
            case "restrictions" -> restrictions = value;
            default -> throw new IllegalArgumentException("Use profile budget|prefs|dislikes|restrictions <value>");
        }
        out.println("Profile saved.\n" + controller.saveProfile(budget, prefs, dislikes, restrictions).describe());
    }

    private void pantry(List<String> rest) {
        String sub = rest.isEmpty() ? "list" : rest.get(0).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> out.println(ResultFormatter.pantry(controller.getPantryItems()));
            case "add" -> {
                require(rest, 4, "pantry add <name> <qty> <unit> [expiry] [kind] [note]");
                PantryItem item = controller.managePantry(rest.get(1), rest.get(2), rest.get(3),
                    rest.size() > 4 ? rest.get(4) : "", rest.size() > 5 ? rest.get(5) : "",
                    rest.size() > 6 ? String.join(" ", rest.subList(6, rest.size())) : "");
                out.println("Pantry now has: " + item);
            }
            case "remove" -> {
                int n = controller.removePantryItem(String.join(" ", rest.subList(1, rest.size())));
                out.println(n == 0 ? "No such item." : "Removed " + n + " entr" + (n == 1 ? "y." : "ies."));
            }
            case "expiring" -> out.println(ResultFormatter.pantry(controller.expiringWithin(
                rest.size() > 1 ? rest.get(1) : "3")));
            case "leftovers" -> out.println(ResultFormatter.pantry(controller.findLeftovers()));
            case "expired" -> out.println(ResultFormatter.pantry(controller.expiredItems()));
            default -> throw new IllegalArgumentException("Use pantry [list|add|remove|expiring|leftovers|expired]");
        }
    }

    /** Parses --priority/--days/--meals/--servings/--note options; other words become the note. */
    private PlanRequest request(List<String> rest) {
        Map<String, String> opts = new HashMap<>();
        List<String> loose = new ArrayList<>();
        for (int i = 0; i < rest.size(); i++) {
            String a = rest.get(i);
            if (a.startsWith("--")) {
                if (i + 1 >= rest.size()) {
                    throw new IllegalArgumentException("Missing value for " + a);
                }
                opts.put(a.substring(2).toLowerCase(Locale.ROOT), rest.get(++i));
            } else {
                loose.add(a);
            }
        }
        String note = opts.getOrDefault("note", String.join(" ", loose));
        return controller.buildRequest(opts.get("priority"), opts.get("days"), opts.getOrDefault("meals", "dinner"),
            opts.get("servings"), note);
    }

    private void progress(String message) {
        if (controller.getFacade().isLlmAvailable()) {
            out.println(message + " (calling the LLM)...");
        }
    }

    private static void require(List<String> args, int n, String usage) {
        if (args.size() < n) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private static String joined(List<String> args, String what) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Please provide the " + what);
        }
        return String.join(" ", args);
    }

    /** Splits a line on whitespace, keeping "double quoted" groups together. */
    static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        if (line == null) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean has = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
                has = true;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (has) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    has = false;
                }
            } else {
                current.append(c);
                has = true;
            }
        }
        if (has) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
