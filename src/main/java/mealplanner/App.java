package mealplanner;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import mealplanner.config.AppConfig;
import mealplanner.controller.MealPlannerController;
import mealplanner.controller.MealPlanningFacade;
import mealplanner.ui.MealPlannerCLI;
import mealplanner.ui.MealPlannerGUI;

/**
 * Application entry point.
 *
 * <pre>
 *   java -jar target/meal-planner.jar                              # GUI (CLI on a headless system)
 *   java -jar target/meal-planner.jar --cli                        # interactive command line
 *   java -jar target/meal-planner.jar --input examples/task.txt    # run a task file, then exit
 *   java -jar target/meal-planner.jar -LLMAPI -input task.txt      # same, and require the LLM API
 *   java -jar target/meal-planner.jar --cli &lt; task.txt             # commands from standard input
 * </pre>
 *
 * Options may be written with one or two dashes and in any letter case.
 */
public final class App {

    /** Exit code for invalid options or an unreadable input file. */
    public static final int EXIT_USAGE = 2;
    /** Exit code when --llm is given but no LLM API key is configured. */
    public static final int EXIT_NO_LLM = 3;

    static final String USAGE = String.join("\n",
        "Usage: java -jar target/meal-planner.jar [options]",
        "",
        "Options (one or two dashes, any letter case):",
        "  (none)               start the GUI; falls back to the CLI if there is no display",
        "  --cli                start the interactive command-line interface",
        "  --input <file>       run the CLI commands in <file> (one per line, # for comments), then exit",
        "  --llm, --llmapi      require the LLM API configured in .env; exit with an error if it is missing",
        "  --no-llm             run without the LLM (hybrid features use their deterministic path)",
        "  --echo               print each command before its output (useful with piped input)",
        "  --help               show this help",
        "",
        "Examples:",
        "  java -jar target/meal-planner.jar --input examples/task.txt",
        "  java -jar target/meal-planner.jar -LLMAPI -input examples/task.txt",
        "  java -jar target/meal-planner.jar --cli < examples/task.txt");

    private App() {
    }

    public static void main(String[] args) {
        int code = run(args, System.in, System.out, System.err, AppConfig.load(),
            !GraphicsEnvironment.isHeadless());
        if (code != 0) {
            System.exit(code);
        }
    }

    /** Parsed command-line options. */
    record Options(boolean cli, String inputFile, boolean requireLlm, boolean noLlm, boolean echo, boolean help) {

        /**
         * Parses the arguments.
         *
         * @throws IllegalArgumentException for unknown options or a missing file name
         */
        static Options parse(String[] args) {
            boolean cli = false;
            String input = null;
            boolean llm = false;
            boolean noLlm = false;
            boolean echo = false;
            boolean help = false;
            for (int i = 0; i < args.length; i++) {
                String opt = args[i].replaceFirst("^--?", "").toLowerCase(Locale.ROOT);
                switch (opt) {
                    case "cli" -> cli = true;
                    case "input", "i", "file" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("Missing file name after " + args[i]);
                        }
                        input = args[++i];
                    }
                    case "llm", "llmapi" -> llm = true;
                    case "no-llm", "nollm", "offline" -> noLlm = true;
                    case "echo" -> echo = true;
                    case "help", "h", "?" -> help = true;
                    default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
                }
            }
            if (llm && noLlm) {
                throw new IllegalArgumentException("Use either --llm or --no-llm, not both");
            }
            return new Options(cli, input, llm, noLlm, echo, help);
        }
    }

    /**
     * Runs the application with the given streams and configuration.
     *
     * @param displayAvailable true if a GUI can be shown
     * @return process exit code (0 on success)
     */
    static int run(String[] args, InputStream in, PrintStream out, PrintStream err, AppConfig config,
                   boolean displayAvailable) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            err.println();
            err.println(USAGE);
            return EXIT_USAGE;
        }
        if (options.help()) {
            out.println(USAGE);
            return 0;
        }
        if (options.requireLlm() && !config.isLlmConfigured()) {
            err.println("Error: --llm was given but no LLM API key is configured. Copy .env.example to .env and set "
                + "LLM_API_KEY (see README).");
            return EXIT_NO_LLM;
        }
        AppConfig effective = options.noLlm()
            ? new AppConfig("", config.getBaseUrl(), config.getModel(), config.getTimeoutSeconds()) : config;

        InputStream commands = in;
        if (options.inputFile() != null) {
            Path file = Path.of(options.inputFile());
            if (!Files.isReadable(file)) {
                err.println("Error: cannot read input file " + file);
                return EXIT_USAGE;
            }
            try {
                commands = Files.newInputStream(file);
            } catch (IOException e) {
                err.println("Error: cannot read input file " + file + ": " + e.getMessage());
                return EXIT_USAGE;
            }
        }

        MealPlannerController controller = new MealPlannerController(MealPlanningFacade.createDefault(effective));
        boolean useCli = options.cli() || options.inputFile() != null || !displayAvailable;
        if (!useCli) {
            MealPlannerGUI.launch(controller);
            return 0;
        }
        if (!options.cli() && options.inputFile() == null) {
            out.println("No display available; starting the command-line interface.");
        }
        try (InputStream source = commands) {
            new MealPlannerCLI(controller, source, out, options.echo() || options.inputFile() != null).run();
        } catch (IOException e) {
            err.println("Error closing input: " + e.getMessage());
        }
        return 0;
    }
}
