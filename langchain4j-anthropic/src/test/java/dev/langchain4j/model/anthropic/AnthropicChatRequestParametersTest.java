package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import org.junit.jupiter.api.Test;

class AnthropicChatRequestParametersTest {

    @Test
    void should_build_with_anthropic_specific_parameters() {
        AnthropicChatRequestParameters parameters = AnthropicChatRequestParameters.builder()
                .temperature(0.7)
                .cacheSystemMessages(true)
                .cacheTools(true)
                .build();

        assertThat(parameters.temperature()).isEqualTo(0.7);
        assertThat(parameters.cacheSystemMessages()).isTrue();
        assertThat(parameters.cacheTools()).isTrue();
    }

    @Test
    void should_default_anthropic_specific_parameters_to_null() {
        assertThat(AnthropicChatRequestParameters.EMPTY.cacheSystemMessages()).isNull();
        assertThat(AnthropicChatRequestParameters.EMPTY.cacheTools()).isNull();
    }

    @Test
    void overrideWith_should_override_anthropic_specific_parameters() {
        AnthropicChatRequestParameters original = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(true)
                .cacheTools(true)
                .build();

        AnthropicChatRequestParameters override = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(false)
                .build();

        AnthropicChatRequestParameters result = original.overrideWith(override);

        assertThat(result.cacheSystemMessages()).isFalse();
        // not set in the override, so the original value is kept
        assertThat(result.cacheTools()).isTrue();
    }

    @Test
    void overrideWith_should_keep_anthropic_specific_parameters_when_overriding_with_common_parameters() {
        AnthropicChatRequestParameters original = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(true)
                .cacheTools(true)
                .build();

        AnthropicChatRequestParameters result = original.overrideWith(
                DefaultChatRequestParameters.builder().temperature(0.1).build());

        assertThat(result.temperature()).isEqualTo(0.1);
        assertThat(result.cacheSystemMessages()).isTrue();
        assertThat(result.cacheTools()).isTrue();
    }

    @Test
    void equals_and_hashCode() {
        AnthropicChatRequestParameters one = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(true)
                .cacheTools(true)
                .build();
        AnthropicChatRequestParameters two = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(true)
                .cacheTools(true)
                .build();
        AnthropicChatRequestParameters different = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(false)
                .cacheTools(true)
                .build();

        assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
        assertThat(one).isNotEqualTo(different);
    }
}
