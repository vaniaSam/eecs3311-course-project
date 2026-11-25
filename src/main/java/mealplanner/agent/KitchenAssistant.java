package mealplanner.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * F12 (new in Stage 2): a conversational kitchen assistant that answers
 * free-form questions by <em>reasoning and calling tools</em> in a loop
 * (ReAct style) and that keeps short-term <em>memory</em> of the conversation.
 *
 * <p>Each LLM turn must return either a tool call
 * {@code {"thought":..,"tool":..,"input":{..}}} or a final answer
 * {@code {"thought":..,"answer":..}}. Tool results are fed back as
 * observations. All tools are read-only, so the assistant can inform but never
 * change the user's data; changes go through the dedicated features.</p>
 */
public class KitchenAssistant {

    /** Maximum reasoning/tool steps per question. */
    public static final int MAX_STEPS = 8;
    /** Number of past question/answer messages remembered. */
    public static final int MEMORY_MESSAGES = 8;
    private static final int MAX_OBSERVATION_CHARS = 3000;
    /** Correction messages sent when the provider cannot read the model's reply. */
    static final int MAX_CORRECTIONS = 2;

    private final LLMClient llm;
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final List<ChatMessage> memory = new ArrayList<>();

    public KitchenAssistant(LLMClient llm, List<AgentTool> tools) {
        this.llm = llm;
        for (AgentTool t : tools) {
            this.tools.put(t.name(), t);
        }
    }

    /** Answers a question using the tool loop. Never throws for LLM problems; the reply explains them. */
    public AssistantReply ask(String question) {
        AgentTrace trace = new AgentTrace();
        List<String> calls = new ArrayList<>();
        if (question == null || question.isBlank()) {
            return new AssistantReply("Please type a question.", calls, false, trace);
        }
        if (!llm.isAvailable()) {
            trace.add("LLM not configured");
            return new AssistantReply("The assistant needs an LLM. Current LLM: " + llm.describe() + ".", calls,
                false, trace);
        }
        List<ChatMessage> convo = new ArrayList<>();
        convo.add(ChatMessage.system(systemPrompt()));
        convo.addAll(memory);
        convo.add(ChatMessage.user(question.trim()));

        int corrections = 0;
        for (int step = 1; step <= MAX_STEPS; step++) {
            String reply;
            try {
                reply = llm.complete(convo);
            } catch (LLMException e) {
                if (e.isProviderParseFailure() && corrections < MAX_CORRECTIONS) {
                    // The provider could not read the model's reply; a short correction gives the model a new
                    // conversation to answer instead of repeating the same request.
                    corrections++;
                    trace.add("Step " + step + ": the provider could not read the model's reply; asking it to answer again");
                    convo.add(ChatMessage.user("Your last reply could not be read. Reply again with exactly one JSON "
                        + "object as plain text: either {\"tool\":...,\"input\":{...}} or {\"answer\":\"...\"}."));
                    continue;
                }
                trace.add("Step " + step + ": LLM call failed - " + e.getMessage());
                return new AssistantReply("The AI service failed: " + e.getMessage(), calls, false, trace);
            }
            convo.add(ChatMessage.assistant(reply));
            JsonObject json;
            try {
                json = JsonExtractor.extractObject(reply);
            } catch (LLMException e) {
                if (!reply.contains("{") && !reply.isBlank()) {
                    trace.add("Step " + step + ": plain-text answer");
                    remember(question, reply.trim());
                    return new AssistantReply(reply.trim(), calls, true, trace);
                }
                trace.add("Step " + step + ": malformed reply - " + e.getMessage());
                convo.add(ChatMessage.user("Reply with one JSON object only, as instructed."));
                continue;
            }
            String thought = JsonExtractor.string(json, "thought", "");
            if (!thought.isBlank()) {
                trace.add("Step " + step + " thought: " + thought);
            }
            String answer = JsonExtractor.string(json, "answer", null);
            if (answer != null && !answer.isBlank()) {
                remember(question, answer);
                return new AssistantReply(answer, calls, true, trace);
            }
            // Accept our {"tool","input"} format and the native function-call shape {"name","arguments"}.
            String toolName = JsonExtractor.string(json, "tool", JsonExtractor.string(json, "name", ""));
            AgentTool tool = tools.get(toolName);
            String observation;
            if (tool == null) {
                observation = "ERROR: unknown tool '" + toolName + "'. Available: " + String.join(", ", tools.keySet());
            } else {
                JsonObject input = toolInput(json.has("input") ? json.get("input") : json.get("arguments"));
                calls.add(toolName + input);
                try {
                    observation = tool.execute(input);
                } catch (RuntimeException e) {
                    observation = "ERROR: " + e.getMessage();
                }
            }
            if (observation.length() > MAX_OBSERVATION_CHARS) {
                observation = observation.substring(0, MAX_OBSERVATION_CHARS) + "\n...(truncated)";
            }
            trace.add("Step " + step + ": " + (tool == null ? "invalid tool " + toolName : "called " + toolName)
                + " -> " + firstLine(observation));
            convo.add(ChatMessage.user("OBSERVATION from " + toolName + ":\n" + observation));
        }
        return new AssistantReply("I could not finish within " + MAX_STEPS + " steps. Try a more specific question.",
            calls, false, trace);
    }

    /** Tool input given as a JSON object or as a JSON-encoded string; anything else means no input. */
    private static JsonObject toolInput(JsonElement element) {
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        if (element != null && element.isJsonPrimitive()) {
            try {
                return JsonExtractor.extractObject(element.getAsString());
            } catch (LLMException e) {
                return new JsonObject();
            }
        }
        return new JsonObject();
    }

    /** Forgets the conversation history. */
    public void clearMemory() {
        memory.clear();
    }

    /** Number of remembered messages (for tests and the UI). */
    public int memorySize() {
        return memory.size();
    }

    public List<String> toolNames() {
        return new ArrayList<>(tools.keySet());
    }

    private void remember(String question, String answer) {
        memory.add(ChatMessage.user(question.trim()));
        JsonObject remembered = new JsonObject();
        remembered.addProperty("answer", answer);
        memory.add(ChatMessage.assistant(remembered.toString()));
        while (memory.size() > MEMORY_MESSAGES) {
            memory.remove(0);
        }
    }

    private String systemPrompt() {
        StringBuilder sb = new StringBuilder("You are the kitchen assistant inside a meal-planning application. "
            + "Answer the user's question using the tools to look up their real data; never invent recipes, "
            + "pantry items or prices. Each reply must be exactly one JSON object, either\n"
            + "{\"thought\":\"why\",\"tool\":\"tool_name\",\"input\":{...}}\nor\n"
            + "{\"thought\":\"why\",\"answer\":\"final answer for the user\"}\n"
            + "You cannot change the user's data; tell them which feature to use for changes. "
            + "For questions about the user's meal plan, shopping list, pantry or profile, use get_meal_plan, "
            + "get_shopping_list, list_pantry or get_profile; search_recipes only searches the recipe collection. "
            + "Do not claim food is allergen-safe. Do not use built-in function calling; write the JSON object "
            + "as plain text.\nTOOLS:\n");
        for (AgentTool t : tools.values()) {
            sb.append("- ").append(t.name()).append(": ").append(t.description()).append('\n');
        }
        return sb.toString();
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        String line = nl < 0 ? s : s.substring(0, nl) + " ...";
        return line.length() > 120 ? line.substring(0, 120) + "..." : line;
    }
}
