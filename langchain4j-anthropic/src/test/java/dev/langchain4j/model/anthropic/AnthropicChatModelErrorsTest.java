package dev.langchain4j.model.anthropic;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;

import dev.langchain4j.model.chat.common.AbstractChatModelErrorsTest;
import java.time.Duration;
import me.kpavlov.aimocks.anthropic.MockAnthropic;
import me.kpavlov.aimocks.core.AbstractBuildingStep;

class AnthropicChatModelErrorsTest extends AbstractChatModelErrorsTest<AnthropicChatModel, MockAnthropic> {

    AnthropicChatModelErrorsTest() {
        super(new MockAnthropic(0, true));
    }

    @Override
    protected AnthropicChatModel createModel(final double temperature, final Duration timeout) {
        final var modelBuilder = AnthropicChatModel.builder()
                .apiKey("dummy-key")
                .baseUrl(mock.baseUrl() + "/v1")
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .maxTokens(20)
                .logRequests(true)
                .logResponses(true);
        if (timeout != null) {
            modelBuilder.timeout(timeout);
        }
        return modelBuilder.build();
    }

    @Override
    protected AbstractBuildingStep<?, ?> whenMockMatched(final String question, final double temperature) {
        return mock.messages(req -> {
            req.userMessageContains(question);
            req.temperature(temperature);
        });
    }
}
