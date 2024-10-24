package dev.langchain4j.service;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public abstract class AiServicesSimpleIT {

    protected abstract List<ChatLanguageModel> models();

    interface Assistant {

        Result<String> chat(String userMessage);
    }

    @Test
    void should_answer_simple_question() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatLanguageModel(model)
                    .build();

            String text = "What is the capital of Germany?";

            // when
            Result<String> result = assistant.chat(text);

            // then
            assertThat(result.content()).containsIgnoringCase("Berlin");

            assertThat(result.tokenUsage()).isNotNull();
            assertThat(result.tokenUsage().inputTokenCount()).isGreaterThan(0);
            assertThat(result.tokenUsage().outputTokenCount()).isGreaterThan(0);

            if (assertFinishReason()) {
                assertThat(result.finishReason()).isEqualTo(STOP);
            }

            assertThat(result.sources()).isNull();

            assertThat(result.toolExecutions()).isEmpty();

            verify(model).chat(ChatRequest.builder().messages(UserMessage.from(text)).build());
        }
    }

    protected boolean assertFinishReason() {
        return true;
    }

    // TODO test token usage is summed for tools?
}
