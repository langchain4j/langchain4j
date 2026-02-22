package dev.langchain4j.model.moderation;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.DisabledModelTest;
import dev.langchain4j.model.input.Prompt;
import java.util.List;
import org.junit.jupiter.api.Test;

class DisabledModerationModelTest extends DisabledModelTest<ModerationModel> {
    private ModerationModel model = new DisabledModerationModel();

    public DisabledModerationModelTest() {
        super(ModerationModel.class);
    }

    @Test
    void methodsShouldThrowException() {
        performAssertion(() -> this.model.moderate("Hello"));
        performAssertion(() -> this.model.moderate(Prompt.from("Hello")));
        performAssertion(() -> this.model.moderate((String) null));
        performAssertion(() -> this.model.moderate(List.<String>of()));
        performAssertion(() -> this.model.moderate(TextSegment.textSegment("Hello")));
    }
}
