package mealplanner.agent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/**
 * Test double for {@link LLMClient}: returns queued replies (or computes them
 * from the conversation) and records every conversation it receives so tests
 * can assert on prompts and revision feedback. Used only in tests.
 */
class ScriptedLLMClient implements LLMClient {

    private final Deque<Function<List<ChatMessage>, String>> replies = new ArrayDeque<>();
    final List<List<ChatMessage>> calls = new ArrayList<>();

    ScriptedLLMClient reply(String text) {
        replies.add(c -> text);
        return this;
    }

    ScriptedLLMClient fail(String message) {
        replies.add(c -> {
            throw new LLMException(message);
        });
        return this;
    }

    ScriptedLLMClient compute(Function<List<ChatMessage>, String> f) {
        replies.add(f);
        return this;
    }

    @Override
    public String complete(List<ChatMessage> messages) {
        calls.add(List.copyOf(messages));
        if (replies.isEmpty()) {
            throw new LLMException("script exhausted");
        }
        return replies.poll().apply(messages);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String describe() {
        return "scripted test model";
    }

    String lastUserMessage() {
        List<ChatMessage> last = calls.get(calls.size() - 1);
        return last.get(last.size() - 1).content();
    }
}
