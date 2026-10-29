package dev.langchain4j.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.JsonWriteException;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import org.junit.jupiter.api.Test;

class Jackson3StructuredPromptFactoryTest {

    @StructuredPrompt("Tell me about {{city}}")
    static class CityPrompt {
        String city;

        CityPrompt(String city) {
            this.city = city;
        }
    }

    @StructuredPrompt("Tell me about {{city}}")
    static class SelfReferencingPrompt {
        String city = "Munich";
        SelfReferencingPrompt self = this;
    }

    @Test
    void fields_become_template_variables() {
        assertThat(StructuredPromptProcessor.toPrompt(new CityPrompt("Munich")).text())
                .isEqualTo("Tell me about Munich");
    }

    /**
     * The opt-in module's contract is that a JSON failure surfaces as a LangChain4j type rather than
     * as Jackson's own, so that catching one does not mean depending on Jackson.
     */
    @Test
    void a_prompt_that_cannot_be_serialized_fails_with_a_langchain4j_exception() {
        assertThatThrownBy(() -> StructuredPromptProcessor.toPrompt(new SelfReferencingPrompt()))
                .isInstanceOf(JsonWriteException.class);
    }
}
