package dev.langchain4j.model.openai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatRequestTest {

    private static final double TEMPERATURE = 1.0;
    private static final int SEED = 123;

    @Test
    void should_set_common_parameters_then_OpenAI_specific_parameters() {

        // when
        OpenAiChatRequestParameters parameters = OpenAiChatRequestParameters.builder()
                .temperature(TEMPERATURE) // first set common parameters
                .seed(SEED) // then set OpenAI-specific parameters
                .build();

        // then
        assertThat(parameters.temperature()).isEqualTo(TEMPERATURE);
        assertThat(parameters.seed()).isEqualTo(SEED);
    }

    @Test
    void should_set_OpenAI_specific_parameters_then_common_parameters() {

        // when
        OpenAiChatRequestParameters parameters = OpenAiChatRequestParameters.builder()
                .seed(SEED) // first set OpenAI-specific parameters
                .temperature(TEMPERATURE) // then set common parameters
                .build();

        // then
        assertThat(parameters.seed()).isEqualTo(SEED);
        assertThat(parameters.temperature()).isEqualTo(TEMPERATURE);
    }
}
