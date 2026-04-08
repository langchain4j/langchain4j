package dev.langchain4j.model.vertexai.anthropic.internal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnthropicRequestMapperTest {

    @Test
    void should_map_thinking_budget_and_force_temperature() {
        // given
        Integer thinkingBudget = 1024;
        Double originalTemperature = 0.5;

        // when
        AnthropicRequest request = AnthropicRequestMapper.toRequest(
                "claude-3-7-sonnet@20260215",
                List.of(UserMessage.from("Hello")),
                null, null, 4000, originalTemperature, null, null, null, false, thinkingBudget
        );

        // then
        assertThat(request.thinking).isNotNull();
        assertThat(request.thinking.type).isEqualTo("enabled");
        assertThat(request.thinking.budgetTokens).isEqualTo(1024);

        // Ensure temperature is overridden to 1.0 per Anthropic API spec
        assertThat(request.temperature).isEqualTo(1.0);
    }

    @Test
    void should_not_map_thinking_budget_if_null_or_zero() {
        // given
        Double originalTemperature = 0.5;

        // when
        AnthropicRequest requestZero = AnthropicRequestMapper.toRequest(
                "claude-3-7-sonnet@20260215",
                List.of(UserMessage.from("Hello")),
                null, null, 4000, originalTemperature, null, null, null, false, 0
        );

        AnthropicRequest requestNull = AnthropicRequestMapper.toRequest(
                "claude-3-7-sonnet@20260215",
                List.of(UserMessage.from("Hello")),
                null, null, 4000, originalTemperature, null, null, null, false, null
        );

        // then
        assertThat(requestZero.thinking).isNull();
        assertThat(requestZero.temperature).isEqualTo(originalTemperature);

        assertThat(requestNull.thinking).isNull();
        assertThat(requestNull.temperature).isEqualTo(originalTemperature);
    }
}
