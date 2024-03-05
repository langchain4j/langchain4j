package dev.langchain4j.model.moderation;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.DisabledModelTest;
import dev.langchain4j.model.input.Prompt;
import org.junit.jupiter.api.Test;

import java.util.Collections;

class DisabledModerationModelTest extends DisabledModelTest<ModerationModel> {
    private ModerationModel model = new DisabledModerationModel();

    public DisabledModerationModelTest() {
        super(ModerationModel.class);
    }

    @Test
    void methodsShouldThrowException() {
        performAssertion(() -> this.model.moderate("Hello"));
        performAssertion(() -> this.model.moderate(Prompt.from("Hello")));
        performAssertion(() -> this.model.moderate((ChatMessage) null));
        performAssertion(() -> this.model.moderate(Collections.emptyList()));
        performAssertion(() -> this.model.moderate(TextSegment.textSegment("Hello")));
    }
}