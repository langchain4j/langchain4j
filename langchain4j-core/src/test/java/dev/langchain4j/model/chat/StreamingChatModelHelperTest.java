package dev.langchain4j.model.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class StreamingChatModelHelperTest {

    @Test
    void should_complete_with_the_terminal_chat_response() {
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi there!"))
                .build();

        CompletableFuture<ChatResponse> future =
                StreamingChatModelHelper.chatAsync(modelEmitting(new CompleteResponse(chatResponse)), request());

        assertThat(future).isCompletedWithValue(chatResponse);
    }

    @Test
    void should_fail_when_the_stream_completes_without_a_complete_response() {
        CompletableFuture<ChatResponse> future = StreamingChatModelHelper.chatAsync(modelEmitting(), request());

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::join)
                .isExactlyInstanceOf(CompletionException.class)
                .hasCauseExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed without emitting a CompleteResponse");
    }

    @Test
    void should_return_failed_future_when_the_request_is_invalid() {
        List<Throwable> handledErrors = new ArrayList<>();

        CompletableFuture<ChatResponse> future = StreamingChatModelHelper.chatAsync(
                MockStreamingChatModel.thatResponds("Hi"), null, handledErrors::add);

        assertThat(future)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(IllegalArgumentException.class)
                .withMessage("request cannot be null");
        // the stream reports the invalid request through onError, like any other stream failure
        assertThat(handledErrors).singleElement().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_return_failed_future_when_the_model_throws_instead_of_returning_a_stream() {
        List<Throwable> handledErrors = new ArrayList<>();
        MockStreamingChatModel model = spy(MockStreamingChatModel.thatResponds("Hi"));
        IllegalStateException failure = new IllegalStateException("cannot stream");
        doThrow(failure).when(model).chat(any(ChatRequest.class));

        CompletableFuture<ChatResponse> future = StreamingChatModelHelper.chatAsync(model, request(), handledErrors::add);

        assertThat(future)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isSameAs(failure);
        assertThat(handledErrors).isEmpty();
    }

    @Test
    void should_fail_with_the_stream_failure_when_the_error_handler_throws_an_error() {
        IllegalStateException failure = new IllegalStateException("stream failed");
        List<Thread> errorHandlerThreads = new CopyOnWriteArrayList<>();

        // the mock reports the failure on its own thread
        CompletableFuture<ChatResponse> future = StreamingChatModelHelper.chatAsync(
                MockStreamingChatModel.thatFails(failure), request(), error -> {
                    errorHandlerThreads.add(Thread.currentThread());
                    throw new AssertionError("error handler failed");
                });

        assertThat(future)
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isSameAs(failure);
        assertThat(errorHandlerThreads).singleElement().isNotSameAs(Thread.currentThread());
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(dev.langchain4j.data.message.UserMessage.from("Hi")).build();
    }

    private static StreamingChatModel modelEmitting(ChatModelStreamingEvent... events) {
        return new StreamingChatModel() {

            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
                return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {

                    private int emitted;
                    private boolean terminated;

                    @Override
                    public void request(long n) {
                        if (terminated) {
                            return;
                        }
                        List<ChatModelStreamingEvent> all = List.of(events);
                        while (n-- > 0 && emitted < all.size()) {
                            subscriber.onNext(all.get(emitted++));
                        }
                        if (emitted == all.size()) {
                            terminated = true;
                            subscriber.onComplete();
                        }
                    }

                    @Override
                    public void cancel() {
                        terminated = true;
                    }
                });
            }
        };
    }
}
