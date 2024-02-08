package dev.langchain4j.model.language;

import dev.langchain4j.model.DisabledModelTest;
import dev.langchain4j.model.input.Prompt;
import org.junit.jupiter.api.Test;

class DisabledLanguageModelTest extends DisabledModelTest<LanguageModel> {
    private LanguageModel model = new DisabledLanguageModel();

    public DisabledLanguageModelTest() {
        super(LanguageModel.class);
    }

    @Test
    void methodsShouldThrowException() {
        performAssertion(() -> this.model.generate("Hello"));
        performAssertion(() -> this.model.generate(Prompt.from("Hello")));
    }
}