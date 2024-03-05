package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.DisabledModelTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;

class DisabledStreamingChatLanguageModelTest extends DisabledModelTest<StreamingChatLanguageModel> {
    private StreamingChatLanguageModel model = new DisabledStreamingChatLanguageModel();

    public DisabledStreamingChatLanguageModelTest() {
        super(StreamingChatLanguageModel.class);
    }

    @Test
    void methodsShouldThrowException() {
        performAssertion(() -> this.model.generate("Hello", null));
        performAssertion(() -> this.model.generate(Collections.emptyList(), null));
        performAssertion(() -> this.model.generate(Collections.emptyList(), Collections.emptyList(), null));
        performAssertion(() -> this.model.generate(Collections.emptyList(), (ToolSpecification) null, null));
    }
}