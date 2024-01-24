package dev.langchain4j.model.language;

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Response;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class LanguageModelTest implements WithAssertions {
    public static class EchoLanguageModel implements LanguageModel {
        @Override
        public Response<String> generate(String prompt) {
            return new Response<>(prompt);
        }
    }

    @Test
    public void test_generate() {
        LanguageModel model = new EchoLanguageModel();

        assertThat(model.generate(Prompt.from("text")))
                .isEqualTo(new Response<>("text"));
    }
}