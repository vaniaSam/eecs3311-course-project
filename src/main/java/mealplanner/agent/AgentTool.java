package mealplanner.agent;

import java.util.Objects;
import java.util.function.Function;

import com.google.gson.JsonObject;

/**
 * A read-only capability the {@link KitchenAssistant} may call while
 * reasoning (tool use). Tools return plain text observations.
 */
public interface AgentTool {

    /** Identifier the LLM uses, e.g. "search_recipes". */
    String name();

    /** One-line description including the expected input fields. */
    String description();

    /**
     * Runs the tool.
     *
     * @throws IllegalArgumentException for invalid input (reported back to the LLM)
     */
    String execute(JsonObject input);

    /** Creates a tool from a lambda. */
    static AgentTool of(String name, String description, Function<JsonObject, String> body) {
        Objects.requireNonNull(body);
        return new AgentTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public String execute(JsonObject input) {
                return body.apply(input);
            }
        };
    }
}
