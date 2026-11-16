package mealplanner.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DefaultCaret;

import mealplanner.agent.AssistantReply;
import mealplanner.agent.DietaryRules;
import mealplanner.agent.RecipeGenerationResult;
import mealplanner.controller.MealPlannerController;
import mealplanner.controller.PlanEditResult;
import mealplanner.model.MealPlan;
import mealplanner.model.PantryItem;
import mealplanner.model.PlanRequest;
import mealplanner.model.PlannedMeal.MealType;
import mealplanner.model.Quantities;
import mealplanner.model.Recipe;
import mealplanner.model.ShoppingList;
import mealplanner.model.UserProfile;
import mealplanner.service.RecipeMatch;

/**
 * <b>MVC pattern - View (graphical).</b> A Swing window with one tab per
 * feature group. All actions are forwarded to {@link MealPlannerController};
 * LLM calls run on a background {@link SwingWorker} so the window stays
 * responsive, and AI steps are shown in an "Agent steps" area.
 */
public class MealPlannerGUI extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    private final transient MealPlannerController controller;
    private final JLabel status = new JLabel();
    private final List<JButton> aiButtons = new ArrayList<>();

    // Profile
    private final JTextField budgetField = new JTextField(8);
    private final JTextField prefsField = new JTextField(30);
    private final JTextField dislikesField = new JTextField(30);
    private final JTextField restrictionsField = new JTextField(30);
    private final JTextArea profileOutput = area(6);

    // Pantry
    private final DefaultTableModel pantryModel = new DefaultTableModel(
        new String[] {"Ingredient", "Qty", "Unit", "Expiry", "Kind", "Status", "Note"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable pantryTable = new JTable(pantryModel);
    private final JTextArea pantryOutput = area(5);

    // Recipes
    private final JTextField searchField = new JTextField(28);
    private final DefaultListModel<Recipe> recipeListModel = new DefaultListModel<>();
    private final JList<Recipe> recipeList = new JList<>(recipeListModel);
    private final JTextArea recipeOutput = area(20);
    private final JSpinner scaleSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 50, 1));
    private final JComboBox<String> ingredientCombo = new JComboBox<>();

    // Planner
    private final JComboBox<String> priorityCombo = new JComboBox<>(new String[] {"pantry", "budget"});
    private final JSpinner daysSpinner = new JSpinner(new SpinnerNumberModel(7, 1, 7, 1));
    private final JSpinner servingsSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1));
    private final JCheckBox breakfastBox = new JCheckBox("Breakfast");
    private final JCheckBox lunchBox = new JCheckBox("Lunch");
    private final JCheckBox dinnerBox = new JCheckBox("Dinner", true);
    private final JTextField instructionField = new JTextField(40);
    private final JTextArea plannerOutput = area(22);
    private transient Recipe lastGenerated;

    // Meal plan
    private final JTextArea planOutput = area(16);
    private final JTextArea shoppingOutput = area(16);
    private final JTextField editField = new JTextField(36);
    private final JComboBox<DayOfWeek> dayCombo = new JComboBox<>(DayOfWeek.values());
    private final JComboBox<MealType> mealCombo = new JComboBox<>(MealType.values());
    private final JTextField replaceIdField = new JTextField(8);
    private final JTextArea editOutput = area(8);

    // Assistant
    private final JTextArea chat = area(24);
    private final JTextField questionField = new JTextField(50);
    private final JCheckBox showSteps = new JCheckBox("Show agent steps", true);

    public MealPlannerGUI(MealPlannerController controller) {
        super("AI Recipe & Meal Planning Agent");
        this.controller = controller;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("F01 Profile", profileTab());
        tabs.addTab("F02 Pantry", pantryTab());
        tabs.addTab("F03/F07/F08 Recipes", recipesTab());
        tabs.addTab("F04-F06/F09 AI Planner", plannerTab());
        tabs.addTab("F06/F10/F11 Meal Plan", mealPlanTab());
        tabs.addTab("F12 Assistant", assistantTab());
        tabs.addChangeListener(e -> refreshAll());
        add(tabs, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(status, BorderLayout.SOUTH);
        refreshAll();
        setPreferredSize(new Dimension(1180, 760));
        pack();
        setLocationRelativeTo(null);
    }

    /** Opens the window on the Swing event thread. */
    public static void launch(MealPlannerController controller) {
        SwingUtilities.invokeLater(() -> new MealPlannerGUI(controller).setVisible(true));
    }

    // ------------------------------------------------------------------ tabs

    private JComponent profileTab() {
        JPanel form = new JPanel(new GridBagLayout());
        int row = 0;
        addRow(form, row++, "Weekly budget ($, 0 = no limit)", budgetField);
        addRow(form, row++, "Preferences (comma separated)", prefsField);
        addRow(form, row++, "Disliked ingredients", dislikesField);
        addRow(form, row++, "Dietary restrictions", restrictionsField);
        addRow(form, row++, "", new JLabel("Built-in: " + String.join(", ", DietaryRules.knownRestrictions())
            + ". Others (e.g. \"no mushrooms\") are treated as ingredients to avoid."));
        JButton save = new JButton("Save profile");
        save.addActionListener(e -> run(() -> {
            UserProfile p = controller.saveProfile(budgetField.getText(), prefsField.getText(),
                dislikesField.getText(), restrictionsField.getText());
            profileOutput.setText("Profile saved.\n\n" + p.describe());
        }, profileOutput));
        addRow(form, row, "", save);
        return north(form, scroll(profileOutput));
    }

    private JComponent pantryTab() {
        JTextField name = new JTextField(12);
        JTextField qty = new JTextField(5);
        JTextField unit = new JTextField(5);
        JTextField expiry = new JTextField(9);
        expiry.setToolTipText("YYYY-MM-DD, +N for N days from today, or blank if unknown");
        JComboBox<String> kind = new JComboBox<>(new String[] {"ingredient", "leftover"});
        JTextField note = new JTextField(10);
        JButton add = new JButton("Add / update");
        add.addActionListener(e -> run(() -> {
            PantryItem item = controller.managePantry(name.getText(), qty.getText(), unit.getText(), expiry.getText(),
                (String) kind.getSelectedItem(), note.getText());
            pantryOutput.setText("Pantry now has: " + item);
            refreshPantry();
        }, pantryOutput));
        JPanel addRow = flow(new JLabel("Name"), name, new JLabel("Qty"), qty, new JLabel("Unit"), unit,
            new JLabel("Expiry"), expiry, kind, new JLabel("Note"), note, add);

        JButton remove = new JButton("Remove selected");
        remove.addActionListener(e -> run(() -> {
            int r = pantryTable.getSelectedRow();
            if (r < 0) {
                throw new IllegalArgumentException("Select a pantry row first");
            }
            String ingredient = (String) pantryModel.getValueAt(r, 0);
            pantryOutput.setText("Removed " + controller.removePantryItem(ingredient) + " entry/entries for "
                + ingredient);
            refreshPantry();
        }, pantryOutput));
        JSpinner days = new JSpinner(new SpinnerNumberModel(3, 0, 60, 1));
        JButton expiring = new JButton("Expiring within days");
        expiring.addActionListener(e -> run(() -> pantryOutput.setText("Expiring within " + days.getValue()
            + " day(s):\n" + ResultFormatter.pantry(controller.expiringWithin(String.valueOf(days.getValue())))),
            pantryOutput));
        JButton leftovers = new JButton("Show leftovers");
        leftovers.addActionListener(e -> pantryOutput.setText("Usable leftovers:\n"
            + ResultFormatter.pantry(controller.findLeftovers())));
        JButton expired = new JButton("Show expired");
        expired.addActionListener(e -> pantryOutput.setText("Expired (never counted as available):\n"
            + ResultFormatter.pantry(controller.expiredItems())));
        JPanel actions = flow(remove, days, expiring, leftovers, expired);

        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(addRow);
        top.add(actions);
        pantryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(pantryTable),
            scroll(pantryOutput));
        split.setResizeWeight(0.7);
        return north(top, split);
    }

    private JComponent recipesTab() {
        JButton keyword = new JButton("Keyword search");
        keyword.addActionListener(e -> run(() -> showRecipes(controller.searchRecipes(searchField.getText()),
            "Keyword results for \"" + searchField.getText() + "\""), recipeOutput));
        JButton byIngredients = new JButton("Search by ingredients");
        byIngredients.setToolTipText("Comma-separated ingredient names");
        byIngredients.addActionListener(e -> run(() -> showMatches(
            controller.searchByIngredients(searchField.getText())), recipeOutput));
        JButton byPantry = new JButton("Use my pantry");
        byPantry.addActionListener(e -> run(() -> showMatches(controller.searchByPantry()), recipeOutput));
        JButton all = new JButton("All recipes");
        all.addActionListener(e -> showRecipes(controller.getAllRecipes(), "All recipes"));

        recipeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recipeList.addListSelectionListener(e -> {
            Recipe r = recipeList.getSelectedValue();
            if (!e.getValueIsAdjusting() && r != null) {
                recipeOutput.setText(r.describe());
                ingredientCombo.removeAllItems();
                r.getIngredients().forEach(ingredientCombo::addItem);
            }
        });

        JButton scale = new JButton("Scale (F07)");
        scale.addActionListener(e -> run(() -> {
            Recipe r = selectedRecipe();
            recipeOutput.setText("Scaled from " + r.getServings() + " to " + scaleSpinner.getValue()
                + " servings (original unchanged):\n\n"
                + controller.scaleRecipe(r.getId(), String.valueOf(scaleSpinner.getValue())).describe());
        }, recipeOutput));
        JButton substitute = aiButton("Suggest substitutes (F08)");
        substitute.addActionListener(e -> {
            Recipe r = recipeList.getSelectedValue();
            String ingredient = (String) ingredientCombo.getSelectedItem();
            background(recipeOutput, () -> {
                if (r == null || ingredient == null) {
                    throw new IllegalArgumentException("Select a recipe and an ingredient first");
                }
                return ResultFormatter.substitutions(controller.suggestSubstitutions(r.getId(), ingredient), true);
            }, recipeOutput::setText);
        });

        JPanel search = flow(new JLabel("Search"), searchField, keyword, byIngredients, byPantry, all);
        JPanel recipeActions = flow(new JLabel("Servings"), scaleSpinner, scale, new JLabel("Ingredient"),
            ingredientCombo, substitute);
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(search);
        top.add(recipeActions);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(recipeList),
            scroll(recipeOutput));
        split.setDividerLocation(330);
        showRecipes(controller.getAllRecipes(), "All recipes");
        return north(top, split);
    }

    private JComponent plannerTab() {
        JPanel request = flow(new JLabel("Priority"), priorityCombo, new JLabel("Days"), daysSpinner,
            new JLabel("Servings/meal"), servingsSpinner, breakfastBox, lunchBox, dinnerBox);
        JPanel instruction = flow(new JLabel("Instruction for the AI (optional)"), instructionField);

        JButton recommend = aiButton("Recommend recipes (F04)");
        recommend.addActionListener(e -> background(plannerOutput,
            () -> ResultFormatter.recommendations(controller.recommendRecipes(request()), true),
            plannerOutput::setText));
        JButton generateRecipe = aiButton("Generate AI recipe (F05)");
        JButton saveRecipe = new JButton("Save generated recipe");
        saveRecipe.setEnabled(false);
        generateRecipe.addActionListener(e -> background(plannerOutput, () -> {
            RecipeGenerationResult r = controller.generateRecipe(request());
            lastGenerated = r.recipe();
            return ResultFormatter.recipeGeneration(r, true);
        }, text -> {
            plannerOutput.setText(text);
            saveRecipe.setEnabled(lastGenerated != null);
        }));
        saveRecipe.addActionListener(e -> run(() -> {
            Recipe saved = controller.saveGeneratedRecipe(lastGenerated);
            lastGenerated = null;
            saveRecipe.setEnabled(false);
            plannerOutput.append("\n\nSaved to the recipe collection as " + saved.getId() + ".");
            showRecipes(controller.getAllRecipes(), "All recipes");
        }, plannerOutput));
        JButton plan = aiButton("Generate weekly plan (F06)");
        plan.addActionListener(e -> background(plannerOutput,
            () -> ResultFormatter.plan(controller.generatePlan(request()), true), text -> {
                plannerOutput.setText(text);
                refreshPlan();
            }));
        JButton leftovers = aiButton("Plan from leftovers (F09)");
        leftovers.addActionListener(e -> background(plannerOutput,
            () -> ResultFormatter.leftovers(controller.planFromLeftovers(request()), true), plannerOutput::setText));

        JPanel top = new JPanel(new GridLayout(3, 1));
        top.add(request);
        top.add(instruction);
        top.add(flow(recommend, generateRecipe, saveRecipe, plan, leftovers));
        return north(top, scroll(plannerOutput));
    }

    private JComponent mealPlanTab() {
        JButton apply = aiButton("Apply edit (F11)");
        apply.addActionListener(e -> background(editOutput,
            () -> ResultFormatter.edit(controller.modifyPlan(editField.getText()), true), text -> {
                editOutput.setText(text);
                refreshPlan();
            }));
        JButton undo = new JButton("Undo last edit");
        undo.addActionListener(e -> {
            PlanEditResult r = controller.undoLastEdit();
            editOutput.setText(r.applied() ? r.message() : "Nothing to undo.");
            refreshPlan();
        });
        JButton replace = new JButton("Replace slot");
        replace.addActionListener(e -> run(() -> {
            PlanEditResult r = controller.replaceMeal(String.valueOf(dayCombo.getSelectedItem()),
                String.valueOf(mealCombo.getSelectedItem()), replaceIdField.getText());
            editOutput.setText((r.applied() ? "Applied: " : "No change: ") + r.message());
            refreshPlan();
        }, editOutput));
        JPanel editRow = flow(new JLabel("Change the plan in plain English"), editField, apply, undo);
        JPanel manualRow = flow(new JLabel("Manual:"), dayCombo, mealCombo, new JLabel("recipe id"), replaceIdField,
            replace);
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(editRow);
        top.add(manualRow);
        planOutput.setBorder(BorderFactory.createTitledBorder("Current plan"));
        shoppingOutput.setBorder(BorderFactory.createTitledBorder("Shopping list (F10, auto-updated)"));
        JSplitPane lists = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll(planOutput), scroll(shoppingOutput));
        lists.setResizeWeight(0.55);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, lists, scroll(editOutput));
        split.setResizeWeight(0.72);
        return north(top, split);
    }

    private JComponent assistantTab() {
        JButton ask = aiButton("Ask");
        Runnable send = () -> {
            String q = questionField.getText();
            if (q.isBlank()) {
                return;
            }
            chat.append("You: " + q + "\n");
            questionField.setText("");
            background(chat, () -> {
                AssistantReply r = controller.askAssistant(q);
                return "Assistant: " + ResultFormatter.assistant(r, showSteps.isSelected()) + "\n\n";
            }, chat::append);
        };
        ask.addActionListener(e -> send.run());
        questionField.addActionListener(e -> send.run());
        JButton clear = new JButton("Clear memory");
        clear.addActionListener(e -> {
            controller.clearAssistantMemory();
            chat.append("(assistant memory cleared)\n\n");
        });
        ((DefaultCaret) chat.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        chat.setText("Ask about your pantry, recipes, plan or shopping list, e.g. \"What can I cook tonight with "
            + "food that expires soon?\"\nThe assistant looks up your data with tools and remembers the "
            + "conversation.\n\n");
        return north(flow(new JLabel("Question"), questionField, ask, clear, showSteps), scroll(chat));
    }

    // ------------------------------------------------------------------ helpers

    private PlanRequest request() {
        List<String> meals = new ArrayList<>();
        if (breakfastBox.isSelected()) {
            meals.add("breakfast");
        }
        if (lunchBox.isSelected()) {
            meals.add("lunch");
        }
        if (dinnerBox.isSelected()) {
            meals.add("dinner");
        }
        return controller.buildRequest((String) priorityCombo.getSelectedItem(), String.valueOf(daysSpinner.getValue()),
            String.join(",", meals), String.valueOf(servingsSpinner.getValue()), instructionField.getText());
    }

    private Recipe selectedRecipe() {
        Recipe r = recipeList.getSelectedValue();
        if (r == null) {
            throw new IllegalArgumentException("Select a recipe in the list first");
        }
        return r;
    }

    private void showRecipes(List<Recipe> recipes, String title) {
        recipeListModel.clear();
        recipes.forEach(recipeListModel::addElement);
        recipeOutput.setText(title + ": " + recipes.size() + " recipe(s)\n\n" + ResultFormatter.recipes(recipes)
            + "\n\nSelect a recipe to see details, scale it or ask for substitutes.");
    }

    private void showMatches(List<RecipeMatch> matches) {
        recipeListModel.clear();
        matches.forEach(m -> recipeListModel.addElement(m.recipe()));
        recipeOutput.setText(matches.size() + " recipe(s) use these ingredients (cookable first):\n\n"
            + ResultFormatter.matches(matches));
    }

    private void refreshAll() {
        UserProfile p = controller.getProfile();
        budgetField.setText(Quantities.format(p.getWeeklyBudget()));
        prefsField.setText(String.join(", ", p.getPreferences()));
        dislikesField.setText(String.join(", ", p.getDislikedIngredients()));
        restrictionsField.setText(String.join(", ", p.getRestrictions()));
        if (profileOutput.getText().isBlank()) {
            profileOutput.setText(p.describe());
        }
        refreshPantry();
        refreshPlan();
        status.setText(controller.llmStatus() + "   |   strategy: " + controller.getFacade().currentStrategyName());
    }

    private void refreshPantry() {
        pantryModel.setRowCount(0);
        LocalDate today = LocalDate.now();
        for (PantryItem i : controller.getPantryItems()) {
            String state = i.isExpired(today) ? "EXPIRED" : i.expiresWithin(3, today) ? "expires soon" : "";
            pantryModel.addRow(new Object[] {i.getIngredientName(), Quantities.format(i.getAmount()), i.getUnit(),
                i.getExpiryDate() == null ? "-" : i.getExpiryDate().toString(), i.getKind(), state,
                i.getStorageNote()});
        }
    }

    private void refreshPlan() {
        MealPlan plan = controller.getCurrentPlan();
        if (plan == null) {
            planOutput.setText("No plan yet. Generate one on the AI Planner tab.");
            shoppingOutput.setText("");
            return;
        }
        planOutput.setText(plan.describe());
        ShoppingList list = controller.getShoppingList();
        shoppingOutput.setText(list.describe() + "\n\n(recalculated " + controller.getFacade()
            .getShoppingListRefreshCount() + " time(s) by the plan observer)");
    }

    /** Runs a quick action on the event thread, showing validation errors in {@code target}. */
    private void run(Runnable action, JTextArea target) {
        try {
            action.run();
            status.setText(controller.llmStatus());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            target.setText("Error: " + ex.getMessage());
        }
    }

    /** Runs a potentially slow (LLM) action on a worker thread and delivers its text on the event thread. */
    private void background(JTextArea target, Supplier<String> work, Consumer<String> done) {
        setBusy(true);
        if (controller.getFacade().isLlmAvailable()) {
            status.setText("Working... calling " + controller.llmStatus());
        }
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return work.get();
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    done.accept(get());
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    target.setText("Error: " + (cause == null ? ex.getMessage() : cause.getMessage()));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                refreshAll();
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
        aiButtons.forEach(b -> b.setEnabled(!busy));
    }

    private JButton aiButton(String label) {
        JButton b = new JButton(label);
        aiButtons.add(b);
        return b;
    }

    private static JTextArea area(int rows) {
        JTextArea a = new JTextArea(rows, 60);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setFont(MONO);
        // Keep results scrolled to the top when replaced; the chat area re-enables auto-scroll.
        ((DefaultCaret) a.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        return a;
    }

    private static JScrollPane scroll(Component c) {
        return new JScrollPane(c);
    }

    private static JPanel flow(Component... components) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (Component c : components) {
            p.add(c);
        }
        return p;
    }

    private static JPanel north(Component top, Component center) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        p.add(top, BorderLayout.NORTH);
        p.add(center, BorderLayout.CENTER);
        return p;
    }

    private static void addRow(JPanel form, int row, String label, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.EAST;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        form.add(field, c);
    }

    /** Shows a modal error; used for start-up failures. */
    static void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "Meal Planner", JOptionPane.ERROR_MESSAGE);
    }
}
