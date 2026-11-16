package mealplanner.agent;

import java.util.List;

/**
 * Result of one {@link KitchenAssistant#ask(String)} call (F12).
 *
 * @param answer    final answer or an explanation of the failure
 * @param toolCalls tools invoked, in order, with their inputs
 * @param success   true if the assistant produced a final answer
 * @param trace     agent steps (thoughts, tool calls, observations)
 */
public record AssistantReply(String answer, List<String> toolCalls, boolean success, AgentTrace trace) {
}
