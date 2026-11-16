package mealplanner.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JList;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import mealplanner.agent.UnconfiguredLLMClient;
import mealplanner.controller.MealPlannerController;
import mealplanner.controller.MealPlanningFacade;
import mealplanner.service.PantryService;
import mealplanner.service.RecipeCatalogLoader;
import mealplanner.service.RecipeService;
import mealplanner.service.SampleData;
import mealplanner.service.ShoppingListService;

/**
 * Drives the real Swing GUI: clicks buttons, checks output and optionally
 * saves screenshots. Skipped in headless runs; run it with
 * {@code xvfb-run mvn test -Dheadless=false -Dtest=GuiSmokeTest -Dscreenshots=docs/screenshots}.
 */
class GuiSmokeTest {

    private MealPlannerGUI gui;

    @Test
    void everyTabWorksThroughTheController() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        MealPlannerController controller = new MealPlannerController(new MealPlanningFacade(SampleData.profile(),
            new RecipeService(RecipeCatalogLoader.loadDefault()), new PantryService(SampleData.pantry()),
            new ShoppingListService(), new UnconfiguredLLMClient()));
        SwingUtilities.invokeAndWait(() -> {
            gui = new MealPlannerGUI(controller);
            gui.setVisible(true);
        });
        JTabbedPane tabs = find(gui, JTabbedPane.class, t -> true);

        // F01
        onEdt(() -> {
            textFields(tabs.getComponentAt(0)).get(3).setText("vegetarian");
            button(tabs.getComponentAt(0), "Save profile").doClick();
        });
        assertTrue(area(tabs.getComponentAt(0)).getText().contains("Dietary restrictions: vegetarian"));
        shot(tabs, 0, "01-profile");

        // F02
        onEdt(() -> {
            List<JTextField> f = textFields(tabs.getComponentAt(1));
            f.get(0).setText("tofu");
            f.get(1).setText("300");
            f.get(2).setText("g");
            f.get(3).setText("+5");
            button(tabs.getComponentAt(1), "Add / update").doClick();
        });
        assertTrue(area(tabs.getComponentAt(1)).getText().contains("300 g tofu"));
        onEdt(() -> button(tabs.getComponentAt(1), "Expiring within days").doClick());
        shot(tabs, 1, "02-pantry");

        // F03 / F07 / F08
        onEdt(() -> {
            button(tabs.getComponentAt(2), "Use my pantry").doClick();
            find(tabs.getComponentAt(2), JList.class, l -> true).setSelectedIndex(0);
            button(tabs.getComponentAt(2), "Scale (F07)").doClick();
        });
        assertTrue(area(tabs.getComponentAt(2)).getText().contains("Scaled from"));
        shot(tabs, 2, "03-recipes-scale");
        onEdt(() -> button(tabs.getComponentAt(2), "Suggest substitutes (F08)").doClick());
        waitFor(() -> area(tabs.getComponentAt(2)).getText().contains("Substitutes for"));
        shot(tabs, 2, "04-substitutes");

        // F04, F06, F09
        onEdt(() -> button(tabs.getComponentAt(3), "Recommend recipes (F04)").doClick());
        waitFor(() -> area(tabs.getComponentAt(3)).getText().contains("Recommendations"));
        shot(tabs, 3, "05-recommend");
        onEdt(() -> button(tabs.getComponentAt(3), "Generate weekly plan (F06)").doClick());
        waitFor(() -> area(tabs.getComponentAt(3)).getText().contains("Plan ACCEPTED"));
        shot(tabs, 3, "06-plan");
        onEdt(() -> button(tabs.getComponentAt(3), "Plan from leftovers (F09)").doClick());
        waitFor(() -> area(tabs.getComponentAt(3)).getText().contains("leftovers"));
        shot(tabs, 3, "07-leftovers");

        // F10 / F11
        String day = LocalDate.now().getDayOfWeek().name().toLowerCase(Locale.ROOT);
        onEdt(() -> {
            tabs.setSelectedIndex(4);
            textFields(tabs.getComponentAt(4)).get(0).setText("remove " + day + " dinner");
            button(tabs.getComponentAt(4), "Apply edit (F11)").doClick();
        });
        waitFor(() -> areas(tabs.getComponentAt(4)).get(2).getText().contains("Applied: Remove"));
        assertTrue(areas(tabs.getComponentAt(4)).get(1).getText().contains("recalculated"));
        shot(tabs, 4, "08-mealplan-edit");

        // F12 (no LLM configured in this test)
        onEdt(() -> {
            tabs.setSelectedIndex(5);
            textFields(tabs.getComponentAt(5)).get(0).setText("What expires soon?");
            button(tabs.getComponentAt(5), "Ask").doClick();
        });
        waitFor(() -> area(tabs.getComponentAt(5)).getText().contains("needs an LLM"));
        shot(tabs, 5, "09-assistant");
        onEdt(() -> gui.dispose());
    }

    // ---------------------------------------------------------------- helpers

    private static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 100; i++) {
            AtomicReference<Boolean> ok = new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(() -> ok.set(condition.getAsBoolean()));
            if (ok.get()) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Timed out waiting for GUI update");
    }

    private void shot(JTabbedPane tabs, int index, String name) throws Exception {
        String dir = System.getProperty("screenshots");
        onEdt(() -> tabs.setSelectedIndex(index));
        if (dir == null) {
            return;
        }
        Thread.sleep(150);
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        onEdt(() -> {
            BufferedImage img = new BufferedImage(gui.getWidth(), gui.getHeight(), BufferedImage.TYPE_INT_RGB);
            gui.paint(img.getGraphics());
            image.set(img);
        });
        File out = new File(dir, name + ".png");
        out.getParentFile().mkdirs();
        try {
            ImageIO.write(image.get(), "png", out);
        } catch (IOException e) {
            fail(e);
        }
    }

    private static AbstractButton button(Component root, String text) {
        return find(root, AbstractButton.class, b -> text.equals(b.getText()));
    }

    private static JTextArea area(Component root) {
        return areas(root).get(0);
    }

    private static List<JTextArea> areas(Component root) {
        return all(root, JTextArea.class);
    }

    private static List<JTextField> textFields(Component root) {
        return all(root, JTextField.class).stream().filter(f -> !(f.getParent() instanceof javax.swing.JSpinner
            .DefaultEditor)).filter(f -> f.getClass() == JTextField.class).toList();
    }

    private static <T> T find(Component root, Class<T> type, Predicate<T> p) {
        for (T t : all(root, type)) {
            if (p.test(t)) {
                return t;
            }
        }
        throw new AssertionError("Component not found: " + type.getSimpleName());
    }

    private static <T> List<T> all(Component root, Class<T> type) {
        List<T> out = new ArrayList<>();
        collect(root, type, out);
        return out;
    }

    private static <T> void collect(Component c, Class<T> type, List<T> out) {
        if (type.isInstance(c)) {
            out.add(type.cast(c));
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, out);
            }
        }
    }
}
