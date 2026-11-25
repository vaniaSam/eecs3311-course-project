package mealplanner;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mealplanner.config.AppConfig;

/** Command-line options of the standalone CLI: --input, --llm/--no-llm, --help and stdin. */
class AppTest {

    private static final AppConfig NO_KEY = new AppConfig("", null, null, 5);
    private static final AppConfig WITH_KEY = new AppConfig("test-key", "http://127.0.0.1:1/v1", "m", 5);

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(AppConfig config, String stdin, String... args) {
        return App.run(args, new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
            new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8),
            config, false);
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void optionsAcceptOneOrTwoDashesInAnyCase() {
        App.Options o = App.Options.parse(new String[] {"-LLMAPI", "-input", "task.txt"});
        assertTrue(o.requireLlm());
        assertEquals("task.txt", o.inputFile());
        App.Options o2 = App.Options.parse(new String[] {"--cli", "--no-llm", "--echo"});
        assertTrue(o2.cli() && o2.noLlm() && o2.echo());
        assertThrows(IllegalArgumentException.class, () -> App.Options.parse(new String[] {"--input"}));
        assertThrows(IllegalArgumentException.class, () -> App.Options.parse(new String[] {"--fly"}));
        assertThrows(IllegalArgumentException.class, () -> App.Options.parse(new String[] {"--llm", "--no-llm"}));
    }

    @Test
    void inputFileRunsTheTaskAndExits(@TempDir Path dir) throws IOException {
        Path task = dir.resolve("task.txt");
        Files.writeString(task, "# comment\nprofile restrictions vegetarian\nplan --days 2\nshopping\n");
        assertEquals(0, run(NO_KEY, "", "--input", task.toString()));
        String text = out();
        assertTrue(text.contains("meal-planner> profile restrictions vegetarian"), "commands are echoed");
        assertTrue(text.contains("Dietary restrictions: vegetarian"));
        assertTrue(text.contains("Plan ACCEPTED"));
        assertTrue(text.contains("Shopping list:"));
    }

    @Test
    void bundledExampleTaskRunsWithoutAnLlm() {
        assertEquals(0, run(NO_KEY, "", "--no-llm", "--input", "examples/task.txt"));
        String text = out();
        assertTrue(text.contains("AI disabled"));
        assertTrue(text.contains("Plan ACCEPTED"));
        assertTrue(text.contains("Goodbye."));
    }

    @Test
    void missingFileUnknownOptionAndMissingKeyAreReported() {
        assertEquals(App.EXIT_USAGE, run(NO_KEY, "", "--input", "does-not-exist.txt"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("cannot read input file"));
        assertEquals(App.EXIT_USAGE, run(NO_KEY, "", "--bogus"));
        assertEquals(App.EXIT_NO_LLM, run(NO_KEY, "", "-LLMAPI", "-input", "examples/task.txt"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("no LLM API key is configured"));
    }

    @Test
    void noLlmOverridesAConfiguredKeyAndStdinStillWorks() {
        assertEquals(0, run(WITH_KEY, "status\nexit\n", "--cli", "--no-llm"));
        assertTrue(out().contains("AI disabled"));
    }

    @Test
    void helpPrintsUsage() {
        assertEquals(0, run(NO_KEY, "", "--help"));
        assertTrue(out().contains("--input <file>"));
    }
}
