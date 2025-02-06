package dev.langchain4j.langfuse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ChatMessage;
import dev.langchain4j.model.chat.Response;
import org.junit.jupiter.api.Test;

class TracingChatLanguageModelIT {

    @Test
    void shouldTraceGeneration() {
        // Given
        ChatLanguageModel mockModel = mock(ChatLanguageModel.class);
        LangfuseTracer mockTracer = mock(LangfuseTracer.class);
        ChatMessage message = ChatMessage.userMessage("Hello");
        ChatMessage response = ChatMessage.assistantMessage("Hi");

        when(mockModel.generate(message)).thenReturn(Response.from(response));

        TracingChatLanguageModel tracingModel = new TracingChatLanguageModel(mockModel, mockTracer);

        // When
        Response<ChatMessage> result = tracingModel.generate(message);

        // Then
        verify(mockTracer).startSpan("chat_generation");
        verify(mockTracer).logEvent(eq("input_message"), any());
        verify(mockTracer).logEvent(eq("output_message"), any());
        verify(mockTracer).endSpan();

        assertThat(result.content().text()).isEqualTo("Hi");
    }
}
