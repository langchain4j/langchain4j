package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.DisabledModelTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;

class DisabledChatLanguageModelTest extends DisabledModelTest<ChatLanguageModel> {
    private ChatLanguageModel model = new DisabledChatLanguageModel();

    public DisabledChatLanguageModelTest() {
        super(ChatLanguageModel.class);
    }

    @Test
    void methodsShouldThrowException() {
        performAssertion(() -> this.model.generate("Hello"));
        performAssertion(this.model::generate);
        performAssertion(() -> this.model.generate(Collections.emptyList()));
        performAssertion(() -> this.model.generate(Collections.emptyList(), (ToolSpecification) null));
        performAssertion(() -> this.model.generate(Collections.emptyList(), Collections.emptyList()));
    }
}