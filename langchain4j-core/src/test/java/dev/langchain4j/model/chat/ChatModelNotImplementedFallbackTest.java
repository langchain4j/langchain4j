package dev.langchain4j.model.chat;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class ChatModelNotImplementedFallbackTest {

    static final ChatRequest CHAT_REQUEST =
            ChatRequest.builder().messages(UserMessage.from("hi")).build();

    static final ChatRequestOptions OPTIONS =
            ChatRequestOptions.builder().invocationId(UUID.randomUUID()).build();

    @Test
    void should_fall_back_to_chat_when_model_overrides_chat_instead_of_doChat() {
        ChatModel model = ChatModelMock.thatAlwaysResponds("hello").overridingChatInsteadOfDoChat();

        assertThat(model.chat(CHAT_REQUEST, OPTIONS).aiMessage().text()).isEqualTo("hello");
        assertThat(model.chat(CHAT_REQUEST).aiMessage().text()).isEqualTo("hello");
    }

    @Test
    void should_fail_without_looping_when_model_implements_nothing() {
        ChatModel model = ChatModelMock.thatAlwaysResponds("hello").withoutDoChat();

        assertThatThrownBy(() -> model.chat(CHAT_REQUEST, OPTIONS))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("doChat(ChatRequest) is not implemented by dev.langchain4j.model.chat.mock.ChatModelMock");
        assertThatThrownBy(() -> model.chat(CHAT_REQUEST))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("doChat(ChatRequest) is not implemented by dev.langchain4j.model.chat.mock.ChatModelMock");
    }

    @Test
    void should_not_fall_back_when_not_implemented_comes_from_another_model_called_by_doChat() {
        ChatModel notImplemented = ChatModelMock.thatAlwaysResponds("never").withoutDoChat();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        ChatModelMock outer = ChatModelMock.thatResponds(request -> notImplemented.chat(request).aiMessage())
                .withListeners(new ChatModelListener() {
                    @Override
                    public void onError(ChatModelErrorContext context) {
                        errors.add(context.error());
                    }
                });

        assertThatThrownBy(() -> outer.chat(CHAT_REQUEST, OPTIONS))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("doChat(ChatRequest) is not implemented by dev.langchain4j.model.chat.mock.ChatModelMock");
        assertThat(outer.requests()).hasSize(1);
        assertThat(errors).singleElement().isInstanceOf(UnsupportedFeatureException.class);
    }

    @Test
    void should_not_swallow_other_unsupported_feature_exceptions() {
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        ChatModel model = new ChatModelMock(new UnsupportedFeatureException("tools are not supported"))
                .withListeners(new ChatModelListener() {
                    @Override
                    public void onError(ChatModelErrorContext context) {
                        errors.add(context.error());
                    }
                });

        assertThatThrownBy(() -> model.chat(CHAT_REQUEST, OPTIONS))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("tools are not supported");
        assertThat(errors).singleElement().isInstanceOf(UnsupportedFeatureException.class);
    }

    @Test
    void streaming_should_fall_back_to_chat_when_model_overrides_chat_instead_of_doChat() {
        StreamingChatModel model =
                StreamingChatModelMock.thatAlwaysStreams("hel", "lo").overridingChatInsteadOfDoChat();
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        model.chat(CHAT_REQUEST, OPTIONS, handler);

        assertThat(handler.get().aiMessage().text()).isEqualTo("hello");
    }

    @Test
    void streaming_should_fail_without_looping_when_model_implements_nothing() {
        StreamingChatModel model =
                StreamingChatModelMock.thatAlwaysStreams("hel", "lo").withoutDoChat();

        assertThatThrownBy(() -> model.chat(CHAT_REQUEST, OPTIONS, new TestStreamingChatResponseHandler()))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("doChat(ChatRequest, StreamingChatResponseHandler) is not implemented by dev.langchain4j.model.chat.mock.StreamingChatModelMock");
    }

    @Test
    void publisher_should_fall_back_to_chat_when_model_overrides_chat_instead_of_doChat() {
        StreamingChatModel model =
                StreamingChatModelMock.thatAlwaysStreams("hel", "lo").overridingChatInsteadOfDoChat();

        assertThat(lastResponse(model.chat(CHAT_REQUEST, OPTIONS)))
                .succeedsWithin(5, SECONDS)
                .satisfies(response -> assertThat(response.chatResponse().aiMessage().text()).isEqualTo("hello"));
    }

    @Test
    void publisher_should_fail_without_looping_when_model_implements_nothing() {
        StreamingChatModel model =
                StreamingChatModelMock.thatAlwaysStreams("hel", "lo").withoutDoChat();

        assertThat(lastResponse(model.chat(CHAT_REQUEST, OPTIONS)))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(AsyncNotSupportedException.class);
    }

    @Test
    void sync_fallback_should_notify_listeners_once() {
        List<String> events = new CopyOnWriteArrayList<>();
        ChatModel model = ChatModelMock.thatAlwaysResponds("hello")
                .overridingChatInsteadOfDoChat()
                .withListeners(recordingListener(events));

        model.chat(CHAT_REQUEST, OPTIONS);

        assertThat(events).containsExactly("request:" + OPTIONS.invocationId(), "response:" + OPTIONS.invocationId());
    }

    @Test
    void sync_fallback_should_notify_listeners_once_on_error() {
        List<String> events = new CopyOnWriteArrayList<>();
        ChatModel model = new ChatModelMock(new IllegalStateException("failed"))
                .overridingChatInsteadOfDoChat()
                .withListeners(recordingListener(events));

        assertThatThrownBy(() -> model.chat(CHAT_REQUEST, OPTIONS))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("failed");
        assertThat(events).containsExactly("request:" + OPTIONS.invocationId(), "error:" + OPTIONS.invocationId());
    }

    @Test
    void async_should_fall_back_to_chatAsync_when_model_overrides_it_instead_of_doChatAsync() {
        List<String> events = new CopyOnWriteArrayList<>();
        ChatModel model = ChatModelMock.thatAlwaysResponds("hello")
                .overridingChatInsteadOfDoChat()
                .withListeners(recordingListener(events));

        assertThat(model.chatAsync(CHAT_REQUEST, OPTIONS))
                .succeedsWithin(5, SECONDS)
                .satisfies(response -> assertThat(response.aiMessage().text()).isEqualTo("hello"));
        assertThat(events).containsExactly("request:" + OPTIONS.invocationId(), "response:" + OPTIONS.invocationId());
    }

    @Test
    void async_should_fail_without_looping_when_model_implements_nothing() {
        ChatModel model = ChatModelMock.thatAlwaysResponds("hello").withoutDoChat();

        assertThat(model.chatAsync(CHAT_REQUEST, OPTIONS))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(AsyncNotSupportedException.class);
    }

    @Test
    void streaming_fallback_should_notify_listeners_once() {
        List<String> events = new CopyOnWriteArrayList<>();
        StreamingChatModel model = StreamingChatModelMock.thatAlwaysStreams("hel", "lo")
                .overridingChatInsteadOfDoChat()
                .withListeners(recordingListener(events));
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        model.chat(CHAT_REQUEST, OPTIONS, handler);

        assertThat(handler.get().aiMessage().text()).isEqualTo("hello");
        assertThat(events).containsExactly("request:" + OPTIONS.invocationId(), "response:" + OPTIONS.invocationId());
    }

    @Test
    void streaming_fallback_should_notify_listeners_once_on_error() {
        List<String> events = new CopyOnWriteArrayList<>();
        CompletableFuture<Throwable> error = new CompletableFuture<>();
        StreamingChatModel model = new StreamingChatModelMock(new IllegalStateException("failed"))
                .overridingChatInsteadOfDoChat()
                .withListeners(recordingListener(events));

        model.chat(CHAT_REQUEST, OPTIONS, new StreamingChatResponseHandler() {
            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {}

            @Override
            public void onError(Throwable throwable) {
                error.complete(throwable);
            }
        });

        assertThat(error).succeedsWithin(5, SECONDS).isInstanceOf(IllegalStateException.class);
        assertThat(events).containsExactly("request:" + OPTIONS.invocationId(), "error:" + OPTIONS.invocationId());
    }

    private static ChatModelListener recordingListener(List<String> events) {
        return new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext context) {
                events.add("request:" + context.invocationId());
            }

            @Override
            public void onResponse(ChatModelResponseContext context) {
                events.add("response:" + context.invocationId());
            }

            @Override
            public void onError(ChatModelErrorContext context) {
                events.add("error:" + context.invocationId());
            }
        };
    }

    private static CompletableFuture<CompleteResponse> lastResponse(Flow.Publisher<ChatModelStreamingEvent> publisher) {
        CompletableFuture<CompleteResponse> result = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatModelStreamingEvent event) {
                if (event instanceof CompleteResponse completeResponse) {
                    result.complete(completeResponse);
                }
            }

            @Override
            public void onError(Throwable error) {
                result.completeExceptionally(error);
            }

            @Override
            public void onComplete() {}
        });
        return result;
    }
}
