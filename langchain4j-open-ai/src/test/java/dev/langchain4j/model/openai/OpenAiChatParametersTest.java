package dev.langchain4j.model.openai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatParametersTest {

    private static final int SEED = 123;
    private static final double TEMPERATURE = 1.0;

    @Test
    void should_set_common_parameters_then_OpenAI_specific_parameters() {

        // when
        OpenAiChatParameters openAiChatParameters = OpenAiChatParameters.builder()
                .temperature(TEMPERATURE)// first set common parameters
                .seed(SEED) // then set OpenAI-specific parameters
                .build();

        // then
        assertThat(openAiChatParameters.seed()).isEqualTo(SEED);
        assertThat(openAiChatParameters.temperature()).isEqualTo(TEMPERATURE);
    }

    @Test
    void should_set_OpenAI_specific_parameters_then_common_parameters() {

        // when
        OpenAiChatParameters openAiChatParameters = OpenAiChatParameters.builder()
                .seed(SEED) // first set OpenAI-specific parameters
                .temperature(TEMPERATURE) // then set common parameters
                .build();

        // then
        assertThat(openAiChatParameters.seed()).isEqualTo(SEED);
        assertThat(openAiChatParameters.temperature()).isEqualTo(TEMPERATURE);
    }
}
