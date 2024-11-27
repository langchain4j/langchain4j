package dev.langchain4j.model.openai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatParametersTest {

    @Test
    void test() {

        // given
        int seed = 123;
        double temperature = 1.0;

        // when
        OpenAiChatParameters openAiChatParameters = OpenAiChatParameters.builder()
                .temperature(temperature)
                .seed(seed)
                .build();

        // then
        assertThat(openAiChatParameters.seed()).isEqualTo(seed);
        assertThat(openAiChatParameters.temperature()).isEqualTo(temperature);
    }

    @Test
    void test_reverse_order() {

        // given
        int seed = 1;
        double temperature = 1.0;

        // when
        OpenAiChatParameters openAiChatParameters = OpenAiChatParameters.builder()
                .seed(seed) // first OpenAiChatParameters parameters
                .temperature(temperature) // then ChatParameters parameters
                .build();

        // then
        assertThat(openAiChatParameters.seed()).isEqualTo(seed);
        assertThat(openAiChatParameters.temperature()).isEqualTo(temperature);
    }
}
