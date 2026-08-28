package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import org.junit.jupiter.api.Test;

class MistralAiChatRequestParametersTest {

    @Test
    void should_build_with_mistral_specific_parameters() {
        MistralAiChatRequestParameters parameters = MistralAiChatRequestParameters.builder()
                .temperature(0.7)
                .safePrompt(true)
                .randomSeed(42)
                .sendThinking(true)
                .returnThinking(true)
                .build();

        assertThat(parameters.temperature()).isEqualTo(0.7);
        assertThat(parameters.safePrompt()).isTrue();
        assertThat(parameters.randomSeed()).isEqualTo(42);
        assertThat(parameters.sendThinking()).isTrue();
        assertThat(parameters.returnThinking()).isTrue();
    }

    @Test
    void should_default_mistral_specific_parameters_to_null() {
        assertThat(MistralAiChatRequestParameters.EMPTY.safePrompt()).isNull();
        assertThat(MistralAiChatRequestParameters.EMPTY.randomSeed()).isNull();
        assertThat(MistralAiChatRequestParameters.EMPTY.sendThinking()).isNull();
        assertThat(MistralAiChatRequestParameters.EMPTY.returnThinking()).isNull();
    }

    @Test
    void overrideWith_should_let_the_argument_win_for_provided_values() {
        MistralAiChatRequestParameters base = MistralAiChatRequestParameters.builder()
                .temperature(0.2)
                .safePrompt(false)
                .randomSeed(1)
                .sendThinking(false)
                .build();

        MistralAiChatRequestParameters override = MistralAiChatRequestParameters.builder()
                .safePrompt(true)
                .randomSeed(99)
                .build();

        MistralAiChatRequestParameters result = base.overrideWith(override);

        assertThat(result.safePrompt()).isTrue();
        assertThat(result.randomSeed()).isEqualTo(99);
        assertThat(result.sendThinking()).isFalse();
        assertThat(result.temperature()).isEqualTo(0.2);
    }

    @Test
    void overrideWith_should_keep_provider_fields_when_argument_is_not_mistral_specific() {
        MistralAiChatRequestParameters base = MistralAiChatRequestParameters.builder()
                .safePrompt(true)
                .randomSeed(7)
                .build();

        MistralAiChatRequestParameters result = base.overrideWith(
                DefaultChatRequestParameters.builder().temperature(0.9).build());

        assertThat(result.temperature()).isEqualTo(0.9);
        assertThat(result.safePrompt()).isTrue();
        assertThat(result.randomSeed()).isEqualTo(7);
    }

    @Test
    void defaultedBy_should_let_this_win() {
        MistralAiChatRequestParameters primary =
                MistralAiChatRequestParameters.builder().safePrompt(true).build();

        MistralAiChatRequestParameters fallback = MistralAiChatRequestParameters.builder()
                .safePrompt(false)
                .randomSeed(5)
                .build();

        MistralAiChatRequestParameters result = primary.defaultedBy(fallback);

        assertThat(result.safePrompt()).isTrue();
        assertThat(result.randomSeed()).isEqualTo(5);
    }

    @Test
    void toBuilder_should_round_trip() {
        MistralAiChatRequestParameters parameters = MistralAiChatRequestParameters.builder()
                .modelName("mistral-large-latest")
                .safePrompt(true)
                .randomSeed(42)
                .build();

        assertThat(parameters.toBuilder().build()).isEqualTo(parameters);
    }

    @Test
    void equals_and_hashCode() {
        MistralAiChatRequestParameters a = MistralAiChatRequestParameters.builder()
                .safePrompt(true)
                .randomSeed(42)
                .build();
        MistralAiChatRequestParameters b = MistralAiChatRequestParameters.builder()
                .safePrompt(true)
                .randomSeed(42)
                .build();
        MistralAiChatRequestParameters different = MistralAiChatRequestParameters.builder()
                .safePrompt(true)
                .randomSeed(43)
                .build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(different);
    }

    @Test
    void should_not_equal_a_plain_default_parameters_with_same_common_fields() {
        MistralAiChatRequestParameters mistral =
                MistralAiChatRequestParameters.builder().temperature(0.5).build();
        ChatRequestParameters plain =
                DefaultChatRequestParameters.builder().temperature(0.5).build();

        assertThat(mistral).isNotEqualTo(plain);
        assertThat(plain).isNotEqualTo(mistral);
    }
}
