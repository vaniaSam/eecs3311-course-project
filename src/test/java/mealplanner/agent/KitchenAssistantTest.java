package mealplanner.agent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import mealplanner.model.UserProfile;
import mealplanner.service.PantryService;
import mealplanner.service.RecipeCatalogLoader;
import mealplanner.service.RecipeService;
import mealplanner.service.SampleData;

/** F12 tool-using assistant: tool loop, observations, memory and limits. */
class KitchenAssistantTest {

    private final UserProfile profile = SampleData.profile();

    private KitchenAssistant assistant(LLMClient llm) {
        return new KitchenAssistant(llm, KitchenTools.create(new RecipeService(RecipeCatalogLoader.loadDefault()),
            new PantryService(SampleData.pantry()), new PlanValidator(), () -> profile, () -> null, () -> null));
    }

    @Test
    void callsToolsThenAnswersAndRemembers() {
        ScriptedLLMClient llm = new ScriptedLLMClient()
            .reply("{\"thought\":\"check expiry\",\"tool\":\"expiring_items\",\"input\":{\"days\":2}}")
            .reply("{\"thought\":\"find recipes\",\"tool\":\"recipes_with_ingredients\","
                + "\"input\":{\"ingredients\":\"spinach\"}}")
            .reply("{\"thought\":\"done\",\"answer\":\"Make the Spinach Omelette (r05) today.\"}");
        KitchenAssistant a = assistant(llm);
        AssistantReply reply = a.ask("What should I cook before food goes off?");

        assertTrue(reply.success());
        assertEquals("Make the Spinach Omelette (r05) today.", reply.answer());
        assertEquals(2, reply.toolCalls().size());
        String observation = llm.calls.get(1).get(llm.calls.get(1).size() - 1).content();
        assertTrue(observation.startsWith("OBSERVATION from expiring_items"));
        assertTrue(observation.contains("spinach"), observation);
        assertTrue(llm.calls.get(2).get(llm.calls.get(2).size() - 1).content().contains("Spinach Omelette"));
        assertEquals(2, a.memorySize());

        ScriptedLLMClient llm2 = new ScriptedLLMClient();
        for (int i = 0; i < 6; i++) {
            llm2.reply("{\"answer\":\"Yes\"}");
        }
        KitchenAssistant b = assistant(llm2);
        for (int i = 0; i < 6; i++) {
            b.ask("question " + i);
        }
        assertEquals(KitchenAssistant.MEMORY_MESSAGES, b.memorySize(), "memory is bounded");
    }

    @Test
    void memoryIsSentWithLaterQuestions() {
        ScriptedLLMClient llm = new ScriptedLLMClient().reply("{\"answer\":\"Rice is in the cupboard.\"}")
            .reply("{\"answer\":\"About 1000 g.\"}");
        KitchenAssistant a = assistant(llm);
        a.ask("Do I have rice?");
        a.ask("How much?");
        assertTrue(llm.calls.get(1).stream().anyMatch(m -> m.content().contains("Do I have rice?")));
        a.clearMemory();
        assertEquals(0, a.memorySize());
    }

    @Test
    void unknownToolAndToolErrorsAreFedBack() {
        ScriptedLLMClient llm = new ScriptedLLMClient()
            .reply("{\"tool\":\"delete_everything\",\"input\":{}}")
            .reply("{\"tool\":\"get_recipe\",\"input\":{\"id\":\"nope\"}}")
            .reply("{\"answer\":\"Sorry.\"}");
        AssistantReply reply = assistant(llm).ask("hi");
        assertTrue(reply.success());
        assertTrue(llm.calls.get(1).get(llm.calls.get(1).size() - 1).content().contains("unknown tool"));
        assertTrue(llm.calls.get(2).get(llm.calls.get(2).size() - 1).content().contains("ERROR: unknown recipe id"));
    }

    @Test
    void stopsAfterMaxStepsAndHandlesFailures() {
        ScriptedLLMClient loop = new ScriptedLLMClient();
        for (int i = 0; i < KitchenAssistant.MAX_STEPS; i++) {
            loop.reply("{\"tool\":\"get_profile\",\"input\":{}}");
        }
        AssistantReply reply = assistant(loop).ask("loop forever");
        assertFalse(reply.success());
        assertEquals(KitchenAssistant.MAX_STEPS, loop.calls.size());

        assertFalse(assistant(new ScriptedLLMClient().fail("rate limited")).ask("hi").success());
        assertFalse(assistant(new UnconfiguredLLMClient()).ask("hi").success());
        assertEquals("Plain text works", assistant(new ScriptedLLMClient().reply("Plain text works")).ask("hi")
            .answer());
    }
}
