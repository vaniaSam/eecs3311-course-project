package mealplanner.agent;

import java.util.List;

/**
 * Result of F04 context-aware recommendations.
 *
 * @param recommendations best-first list (may be empty)
 * @param aiUsed          true if the LLM re-ranked/explained the list
 * @param strategyName    strategy that produced the candidate ranking
 * @param notes           explanations such as recipes excluded by restrictions
 * @param trace           agent steps
 */
public record RecommendationResult(List<Recommendation> recommendations, boolean aiUsed, String strategyName,
                                   List<String> notes, AgentTrace trace) {
}
