package dev.langchain4j.internal;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InternalStreamingChatResponseHandlerUtilsTest {

    private StreamingChatResponseHandler handler;
    private StreamingHandle streamingHandle;

    @BeforeEach
    void setUp() {
        handler = mock(StreamingChatResponseHandler.class);
        streamingHandle = mock(StreamingHandle.class);
    }

    @Test
    void withLoggingExceptions_shouldRunRunnableNormally() {
        assertThatCode(() -> InternalStreamingChatResponseHandlerUtils.withLoggingExceptions(() -> {
            // no-op
        })).doesNotThrowAnyException();
    }

    @Test
    void withLoggingExceptions_shouldLogException() {
        Runnable failingRunnable = mock(Runnable.class);
        doThrow(new RuntimeException("fail")).when(failingRunnable).run();

        assertThatCode(() -> InternalStreamingChatResponseHandlerUtils.withLoggingExceptions(failingRunnable))
                .doesNotThrowAnyException();

        verify(failingRunnable).run();
    }

    @Test
    void onPartialResponse_deprecated_shouldCallHandler_whenNotEmpty() {
        String response = "partial";
        InternalStreamingChatResponseHandlerUtils.onPartialResponse(handler, response);

        verify(handler).onPartialResponse(response);
    }

    @Test
    void onPartialResponse_deprecated_shouldDoNothing_whenNullOrEmpty() {
        InternalStreamingChatResponseHandlerUtils.onPartialResponse(handler, null);
        InternalStreamingChatResponseHandlerUtils.onPartialResponse(handler, "");

        verifyNoInteractions(handler);
    }

    @Test
    void onPartialResponse_new_shouldCallHandler_withContext() {
        String response = "partial";
        InternalStreamingChatResponseHandlerUtils.onPartialResponse(handler, response, streamingHandle);

        verify(handler).onPartialResponse(eq(new PartialResponse(response)), any(PartialResponseContext.class));
    }

    @Test
    void onPartialThinking_new_shouldCallHandler_withContext() {
        String thinking = "thinking";
        InternalStreamingChatResponseHandlerUtils.onPartialThinking(handler, thinking, streamingHandle);

        verify(handler).onPartialThinking(eq(new PartialThinking(thinking)), any(PartialThinkingContext.class));
    }

    @Test
    void onPartialToolCall_new_shouldCallHandler_withContext() {
        PartialToolCall toolCall = PartialToolCall.builder()
                .index(0)
                .id("id")
                .name("name")
                .partialArguments("partialArguments")
                .build();
        InternalStreamingChatResponseHandlerUtils.onPartialToolCall(handler, toolCall, streamingHandle);

        verify(handler).onPartialToolCall(eq(toolCall), any(PartialToolCallContext.class));
    }

    @Test
    void onCompleteToolCall_shouldCallHandler() {
        CompleteToolCall completeToolCall = new CompleteToolCall(0, ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("arguments")
                .build());
        InternalStreamingChatResponseHandlerUtils.onCompleteToolCall(handler, completeToolCall);

        verify(handler).onCompleteToolCall(completeToolCall);
    }

    @Test
    void onCompleteResponse_shouldCallHandler() {
        ChatResponse completeResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("does not matter"))
                .build();
        InternalStreamingChatResponseHandlerUtils.onCompleteResponse(handler, completeResponse);

        verify(handler).onCompleteResponse(completeResponse);
    }

    @Test
    void exceptionDuringHandlerCalls_shouldBeHandled() {
        String response = "partial";

        doThrow(new RuntimeException("fail")).when(handler).onPartialResponse(any(PartialResponse.class), any());
        doNothing().when(handler).onError(any());

        assertThatCode(() -> InternalStreamingChatResponseHandlerUtils.onPartialResponse(handler, response, streamingHandle))
                .doesNotThrowAnyException();

        verify(handler).onError(any(RuntimeException.class));
    }
}
