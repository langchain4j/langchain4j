package dev.langchain4j.model.openai.common.responses;

import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesStreamingChatModelThinkingIT {

    @Test
    void should_stream_reasoning_summary() {

        // given
        String reasoningSummary = "auto";

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("gpt-5-mini")
                .reasoningEffort("low")
                .reasoningSummary(reasoningSummary)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());
        model.chat(of(userMessage), spyHandler);

        // then
        ChatResponse chatResponse = spyHandler.get();
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).isNotBlank();
        assertThat(aiMessage.thinking()).isEqualTo(spyHandler.getThinking());

        InOrder inOrder = inOrder(spyHandler);
        inOrder.verify(spyHandler).get();
        inOrder.verify(spyHandler, atLeastOnce()).onPartialThinking(any(), any());
        inOrder.verify(spyHandler, atLeastOnce()).onPartialResponse(any(), any());
        inOrder.verify(spyHandler).onCompleteResponse(any());
        inOrder.verify(spyHandler).getThinking();
        inOrder.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler);
    }

    @Test
    void should_not_stream_reasoning_summary_when_not_requested() {

        // given
        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("gpt-5-mini")
                .reasoningEffort("low")
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());
        model.chat(of(userMessage), spyHandler);

        // then
        ChatResponse chatResponse = spyHandler.get();
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).isNull();

        verify(spyHandler, never()).onPartialThinking(any());
        verify(spyHandler, never()).onPartialThinking(any(), any());
    }
}
