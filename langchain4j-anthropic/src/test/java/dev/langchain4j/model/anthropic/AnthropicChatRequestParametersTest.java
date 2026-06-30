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
                .thinkingType("enabled")
                .thinkingBudgetTokens(1024)
                .sendThinking(true)
                .returnThinking(true)
                .toolChoiceName("get_weather")
                .disableParallelToolUse(true)
                .userId("test-user-123")
                .build();

        assertThat(parameters.temperature()).isEqualTo(0.7);
        assertThat(parameters.cacheSystemMessages()).isTrue();
        assertThat(parameters.cacheTools()).isTrue();
        assertThat(parameters.thinkingType()).isEqualTo("enabled");
        assertThat(parameters.thinkingBudgetTokens()).isEqualTo(1024);
        assertThat(parameters.sendThinking()).isTrue();
        assertThat(parameters.returnThinking()).isTrue();
        assertThat(parameters.toolChoiceName()).isEqualTo("get_weather");
        assertThat(parameters.disableParallelToolUse()).isTrue();
        assertThat(parameters.userId()).isEqualTo("test-user-123");
    }

    @Test
    void should_default_anthropic_specific_parameters_to_null() {
        assertThat(AnthropicChatRequestParameters.EMPTY.cacheSystemMessages()).isNull();
        assertThat(AnthropicChatRequestParameters.EMPTY.cacheTools()).isNull();
        assertThat(AnthropicChatRequestParameters.EMPTY.thinkingType()).isNull();
        assertThat(AnthropicChatRequestParameters.EMPTY.thinkingBudgetTokens()).isNull();
        assertThat(AnthropicChatRequestParameters.EMPTY.sendThinking()).isNull();
        assertThat(AnthropicChatRequestParameters.EMPTY.returnThinking()).isNull();
        assertThat(AnthropicChatRequestParameters.EMPTY.toolChoiceName()).isNull();
        assertThat(AnthropicChatRequestParameters.EMPTY.disableParallelToolUse())
                .isNull();
        assertThat(AnthropicChatRequestParameters.EMPTY.userId()).isNull();
    }

    @Test
    void overrideWith_should_override_anthropic_specific_parameters() {
        AnthropicChatRequestParameters original = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(true)
                .cacheTools(true)
                .thinkingType("enabled")
                .thinkingBudgetTokens(1000)
                .sendThinking(true)
                .returnThinking(true)
                .toolChoiceName("get_weather")
                .disableParallelToolUse(true)
                .userId("user-1")
                .build();

        AnthropicChatRequestParameters override = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(false)
                .thinkingType("disabled")
                .thinkingBudgetTokens(0)
                .sendThinking(false)
                .returnThinking(false)
                .toolChoiceName("get_time")
                .disableParallelToolUse(false)
                .userId("user-2")
                .build();

        AnthropicChatRequestParameters result = original.overrideWith(override);

        assertThat(result.cacheSystemMessages()).isFalse();
        assertThat(result.cacheTools()).isTrue(); // not set in the override, so original value is kept
        assertThat(result.thinkingType()).isEqualTo("disabled");
        assertThat(result.thinkingBudgetTokens()).isEqualTo(0);
        assertThat(result.sendThinking()).isFalse();
        assertThat(result.returnThinking()).isFalse();
        assertThat(result.toolChoiceName()).isEqualTo("get_time");
        assertThat(result.disableParallelToolUse()).isFalse();
        assertThat(result.userId()).isEqualTo("user-2");
    }

    @Test
    void overrideWith_should_keep_anthropic_specific_parameters_when_overriding_with_common_parameters() {
        AnthropicChatRequestParameters original = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(true)
                .cacheTools(true)
                .thinkingType("enabled")
                .thinkingBudgetTokens(1000)
                .sendThinking(true)
                .returnThinking(true)
                .toolChoiceName("get_weather")
                .disableParallelToolUse(true)
                .userId("user-1")
                .build();

        AnthropicChatRequestParameters result = original.overrideWith(
                DefaultChatRequestParameters.builder().temperature(0.1).build());

        assertThat(result.temperature()).isEqualTo(0.1);
        assertThat(result.cacheSystemMessages()).isTrue();
        assertThat(result.cacheTools()).isTrue();
        assertThat(result.thinkingType()).isEqualTo("enabled");
        assertThat(result.thinkingBudgetTokens()).isEqualTo(1000);
        assertThat(result.sendThinking()).isTrue();
        assertThat(result.returnThinking()).isTrue();
        assertThat(result.toolChoiceName()).isEqualTo("get_weather");
        assertThat(result.disableParallelToolUse()).isTrue();
        assertThat(result.userId()).isEqualTo("user-1");
    }

    @Test
    void equals_and_hashCode() {
        AnthropicChatRequestParameters one = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(true)
                .cacheTools(true)
                .thinkingType("enabled")
                .thinkingBudgetTokens(1000)
                .sendThinking(true)
                .returnThinking(true)
                .toolChoiceName("get_weather")
                .disableParallelToolUse(true)
                .userId("user-1")
                .build();
        AnthropicChatRequestParameters two = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(true)
                .cacheTools(true)
                .thinkingType("enabled")
                .thinkingBudgetTokens(1000)
                .sendThinking(true)
                .returnThinking(true)
                .toolChoiceName("get_weather")
                .disableParallelToolUse(true)
                .userId("user-1")
                .build();
        AnthropicChatRequestParameters different = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(false)
                .cacheTools(true)
                .thinkingType("enabled")
                .thinkingBudgetTokens(1000)
                .sendThinking(true)
                .returnThinking(true)
                .toolChoiceName("get_weather")
                .disableParallelToolUse(true)
                .userId("user-1")
                .build();

        assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
        assertThat(one).isNotEqualTo(different);
    }

    @Test
    void defaultedBy_should_apply_defaults_to_anthropic_specific_parameters() {
        AnthropicChatRequestParameters original = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(true)
                .thinkingType("enabled")
                .sendThinking(true)
                .toolChoiceName("get_weather")
                .userId("user-1")
                .build();

        AnthropicChatRequestParameters fallback = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(false) // should be ignored as original has it
                .cacheTools(true) // should be defaulted from fallback
                .thinkingType("disabled") // should be ignored
                .thinkingBudgetTokens(1000) // should be defaulted from fallback
                .sendThinking(false) // should be ignored
                .returnThinking(true) // should be defaulted from fallback
                .toolChoiceName("get_time") // should be ignored
                .disableParallelToolUse(true) // should be defaulted from fallback
                .userId("user-2") // should be ignored
                .build();

        AnthropicChatRequestParameters result = original.defaultedBy(fallback);

        assertThat(result.cacheSystemMessages()).isTrue();
        assertThat(result.cacheTools()).isTrue();
        assertThat(result.thinkingType()).isEqualTo("enabled");
        assertThat(result.thinkingBudgetTokens()).isEqualTo(1000);
        assertThat(result.sendThinking()).isTrue();
        assertThat(result.returnThinking()).isTrue();
        assertThat(result.toolChoiceName()).isEqualTo("get_weather");
        assertThat(result.disableParallelToolUse()).isTrue();
        assertThat(result.userId()).isEqualTo("user-1");
    }

    @Test
    void defaultedBy_should_keep_parameters_when_defaulted_by_common_parameters() {
        AnthropicChatRequestParameters original = AnthropicChatRequestParameters.builder()
                .cacheSystemMessages(true)
                .cacheTools(true)
                .thinkingType("enabled")
                .thinkingBudgetTokens(1000)
                .sendThinking(true)
                .returnThinking(true)
                .toolChoiceName("get_weather")
                .disableParallelToolUse(true)
                .userId("user-1")
                .build();

        AnthropicChatRequestParameters result = original.defaultedBy(
                DefaultChatRequestParameters.builder().temperature(0.1).build());

        assertThat(result.temperature()).isEqualTo(0.1);
        assertThat(result.cacheSystemMessages()).isTrue();
        assertThat(result.cacheTools()).isTrue();
        assertThat(result.thinkingType()).isEqualTo("enabled");
        assertThat(result.thinkingBudgetTokens()).isEqualTo(1000);
        assertThat(result.sendThinking()).isTrue();
        assertThat(result.returnThinking()).isTrue();
        assertThat(result.toolChoiceName()).isEqualTo("get_weather");
        assertThat(result.disableParallelToolUse()).isTrue();
        assertThat(result.userId()).isEqualTo("user-1");
    }

    @Test
    void toBuilder_should_populate_all_fields() {
        AnthropicChatRequestParameters original = AnthropicChatRequestParameters.builder()
                .temperature(0.7)
                .cacheSystemMessages(true)
                .cacheTools(true)
                .thinkingType("enabled")
                .thinkingBudgetTokens(1024)
                .sendThinking(true)
                .returnThinking(true)
                .toolChoiceName("get_weather")
                .disableParallelToolUse(true)
                .userId("test-user-123")
                .build();

        AnthropicChatRequestParameters copy = original.toBuilder().build();

        assertThat(copy).isEqualTo(original);
        assertThat(copy.temperature()).isEqualTo(0.7);
        assertThat(copy.cacheSystemMessages()).isTrue();
        assertThat(copy.cacheTools()).isTrue();
        assertThat(copy.thinkingType()).isEqualTo("enabled");
        assertThat(copy.thinkingBudgetTokens()).isEqualTo(1024);
        assertThat(copy.sendThinking()).isTrue();
        assertThat(copy.returnThinking()).isTrue();
        assertThat(copy.toolChoiceName()).isEqualTo("get_weather");
        assertThat(copy.disableParallelToolUse()).isTrue();
        assertThat(copy.userId()).isEqualTo("test-user-123");
    }

    @Test
    void mid_conversation_system_messages_is_plumbed_through_builder_override_defaultedBy_and_copy() {
        // build + getter
        assertThat(AnthropicChatRequestParameters.builder()
                        .midConversationSystemMessages(true)
                        .build()
                        .midConversationSystemMessages())
                .isTrue();

        // defaults to null
        assertThat(AnthropicChatRequestParameters.EMPTY.midConversationSystemMessages()).isNull();

        // overrideWith takes the other value when set
        AnthropicChatRequestParameters overridden = AnthropicChatRequestParameters.builder()
                .midConversationSystemMessages(true)
                .build()
                .overrideWith(AnthropicChatRequestParameters.builder()
                        .midConversationSystemMessages(false)
                        .build());
        assertThat(overridden.midConversationSystemMessages()).isFalse();

        // defaultedBy keeps the original value
        AnthropicChatRequestParameters defaulted = AnthropicChatRequestParameters.builder()
                .midConversationSystemMessages(true)
                .build()
                .defaultedBy(AnthropicChatRequestParameters.builder()
                        .midConversationSystemMessages(false)
                        .build());
        assertThat(defaulted.midConversationSystemMessages()).isTrue();

        // equals/hashCode distinguish the field
        AnthropicChatRequestParameters on = AnthropicChatRequestParameters.builder()
                .midConversationSystemMessages(true)
                .build();
        AnthropicChatRequestParameters onCopy = AnthropicChatRequestParameters.builder()
                .midConversationSystemMessages(true)
                .build();
        AnthropicChatRequestParameters off = AnthropicChatRequestParameters.builder()
                .midConversationSystemMessages(false)
                .build();
        assertThat(on).isEqualTo(onCopy).hasSameHashCodeAs(onCopy);
        assertThat(on).isNotEqualTo(off);

        // toBuilder round-trips the field
        assertThat(on.toBuilder().build()).isEqualTo(on);
        assertThat(on.toBuilder().build().midConversationSystemMessages()).isTrue();
    }
}
