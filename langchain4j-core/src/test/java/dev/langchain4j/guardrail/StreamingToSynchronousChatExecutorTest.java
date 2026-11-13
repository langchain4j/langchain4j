package dev.langchain4j.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class StreamingToSynchronousChatExecutorTest {

    private static final ChatRequest CHAT_REQUEST =
            ChatRequest.builder().messages(UserMessage.from("Hello")).build();

    @Test
    void execute_should_fail_instead_of_waiting_forever_when_the_stream_fails() {
        IllegalStateException failure = new IllegalStateException("stream failed");
        List<Throwable> handledErrors = new CopyOnWriteArrayList<>();
        ChatExecutor executor = ChatExecutor.builder(new StreamingChatModelMock(failure))
                .errorHandler(handledErrors::add)
                .chatRequest(CHAT_REQUEST)
                .invocationContext(InvocationContext.builder().build())
                .build();

        assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> assertThatThrownBy(executor::execute).isSameAs(failure));
        assertThat(handledErrors).containsExactly(failure);
    }

    @Test
    void execute_should_fail_when_the_model_throws_from_chat() {
        IllegalStateException failure = new IllegalStateException("cannot send the request");
        List<Throwable> handledErrors = new CopyOnWriteArrayList<>();
        ChatExecutor executor = ChatExecutor.builder(new StreamingChatModelMock(failure).throwingFromChat())
                .errorHandler(handledErrors::add)
                .chatRequest(CHAT_REQUEST)
                .invocationContext(InvocationContext.builder().build())
                .build();

        assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> assertThatThrownBy(executor::execute).isSameAs(failure));
        assertThat(handledErrors).containsExactly(failure);
    }

    @Test
    void execute_should_return_the_response_when_the_model_reports_an_error_after_it() {
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from("Hi")).build();
        IllegalStateException lateFailure = new IllegalStateException("closing the stream failed");
        ChatExecutor executor = ChatExecutor.builder(completingThenFailing(response, lateFailure))
                .chatRequest(CHAT_REQUEST)
                .invocationContext(InvocationContext.builder().build())
                .build();

        ChatResponse result = executor.execute();

        assertThat(result.aiMessage().text()).isEqualTo("Hi");
    }

    @Test
    void execute_should_pass_an_error_after_the_response_to_the_error_handler_and_return_the_response() {
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from("Hi")).build();
        IllegalStateException lateFailure = new IllegalStateException("closing the stream failed");
        List<Throwable> handledErrors = new CopyOnWriteArrayList<>();
        ChatExecutor executor = ChatExecutor.builder(completingThenFailing(response, lateFailure))
                .errorHandler(handledErrors::add)
                .chatRequest(CHAT_REQUEST)
                .invocationContext(InvocationContext.builder().build())
                .build();

        ChatResponse result = executor.execute();

        assertThat(result.aiMessage().text()).isEqualTo("Hi");
        assertThat(handledErrors).containsExactly(lateFailure);
    }

    private static StreamingChatModelMock completingThenFailing(ChatResponse response, RuntimeException lateFailure) {
        // like a provider whose cleanup after the end of the stream fails, for example closing the connection
        StreamingChatModelMock model = spy(StreamingChatModelMock.thatAlwaysStreams("Hi"));
        doAnswer(invocation -> {
                    StreamingChatResponseHandler handler = invocation.getArgument(1);
                    handler.onCompleteResponse(response);
                    handler.onError(lateFailure);
                    return null;
                })
                .when(model)
                .doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        return model;
    }
}
