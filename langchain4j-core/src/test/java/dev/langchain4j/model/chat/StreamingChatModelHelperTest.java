package dev.langchain4j.model.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
