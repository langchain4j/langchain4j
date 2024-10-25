package dev.langchain4j.service;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * This test makes sure that all {@link ChatLanguageModel} implementations behave consistently
 * when used with {@link AiServices}.
 */
@TestInstance(PER_CLASS)
public abstract class AiServicesSimpleIT {

    protected abstract List<ChatLanguageModel> models();

    interface Assistant {

        Result<String> chat(String userMessage);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_answer_simple_question(ChatLanguageModel model) {

        // given
        model = spy(model);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .build();

        String userMessage = "What is the capital of Germany?";

        // when
        Result<String> result = assistant.chat(userMessage);

        // then
        assertThat(result.content()).containsIgnoringCase("Berlin");

        TokenUsage tokenUsage = result.tokenUsage();
        assertThat(tokenUsage).isNotNull();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(result.finishReason()).isEqualTo(STOP);
        }

        assertThat(result.sources()).isNull();

        assertThat(result.toolExecutions()).isEmpty();

        verify(model).chat(ChatRequest.builder().messages(UserMessage.from(userMessage)).build());
    }

    protected boolean assertFinishReason() {
        return true;
    }

    // TODO test tool handling in AI Services across models (separate test)

    // TODO test token usage is summed for tools?
}
