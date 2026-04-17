package dev.langchain4j.model.anthropic;

import static dev.langchain4j.model.anthropic.AnthropicChatModel.toThinking;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.langchain4j.model.anthropic.internal.api.AnthropicThinking;
import org.junit.jupiter.api.Test;

class AnthropicChatModelTest {

    @Test
    void toThinking_with_display_summarized() {
        AnthropicThinking thinking = toThinking("adaptive", null, "summarized");

        assertNotNull(thinking);
    }

    @Test
    void toThinking_with_display_omitted() {
        AnthropicThinking thinking = toThinking("adaptive", null, "omitted");

        assertNotNull(thinking);
    }

    @Test
    void toThinking_without_display() {
        AnthropicThinking thinking = toThinking("enabled", 10000, null);

        assertNotNull(thinking);
    }

    @Test
    void toThinking_returns_null_when_all_null() {
        AnthropicThinking thinking = toThinking(null, null, null);

        assertNull(thinking);
    }
}
