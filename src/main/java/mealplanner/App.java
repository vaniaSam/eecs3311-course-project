package mealplanner;

import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;

import mealplanner.config.AppConfig;
import mealplanner.controller.MealPlannerController;
import mealplanner.controller.MealPlanningFacade;
import mealplanner.ui.MealPlannerCLI;
import mealplanner.ui.MealPlannerGUI;

/**
 * Application entry point.
 *
 * <pre>
 *   java -jar target/meal-planner.jar          # GUI (falls back to CLI on a headless system)
 *   java -jar target/meal-planner.jar --cli    # command-line interface
 *   java -jar target/meal-planner.jar --cli --echo &lt; script.txt   # scripted CLI session
 * </pre>
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        List<String> options = Arrays.asList(args);
        AppConfig config = AppConfig.load();
        MealPlannerController controller = new MealPlannerController(MealPlanningFacade.createDefault(config));
        boolean cli = options.contains("--cli") || GraphicsEnvironment.isHeadless();
        if (cli) {
            if (!options.contains("--cli")) {
                System.out.println("No display available; starting the command-line interface.");
            }
            new MealPlannerCLI(controller, System.in, System.out, options.contains("--echo")).run();
        } else {
            MealPlannerGUI.launch(controller);
        }
    }
}
