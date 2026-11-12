package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.CustomChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

class StreamingChatModelTest implements WithAssertions {

    public static class StreamingUpperCaseEchoModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();
            UserMessage lastMessage = (UserMessage) messages.get(messages.size() - 1);
            ChatResponse chatResponse = ChatResponse.builder()
                    .aiMessage(new AiMessage(lastMessage.singleText().toUpperCase(Locale.ROOT)))
                    .build();
            handler.onCompleteResponse(chatResponse);
        }
    }

    public static final class CollectorResponseHandler implements StreamingChatResponseHandler {

        private final List<ChatResponse> responses = new ArrayList<>();
        private final List<Object> rawEvents = new ArrayList<>();

        public List<ChatResponse> responses() {
            return responses;
        }

        public List<Object> rawEvents() {
            return rawEvents;
        }

        @Override
        public void onPartialResponse(String partialResponse) {}

        @Override
        public void onUnmappedRawEvent(Object rawEvent) {
            rawEvents.add(rawEvent);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            responses.add(completeResponse);
        }

        @Override
        public void onError(Throwable error) {}
    }

    @Test
    void generate() {
        StreamingChatModel model = new StreamingUpperCaseEchoModel();

        {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("Hello"));
            messages.add(new AiMessage("Hi"));
            messages.add(new UserMessage("How are you?"));

            CollectorResponseHandler handler = new CollectorResponseHandler();
            model.chat(messages, handler);

            ChatResponse response = handler.responses().get(0);

            assertThat(response.aiMessage().text()).isEqualTo("HOW ARE YOU?");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
        }

        {
            CollectorResponseHandler handler = new CollectorResponseHandler();
            model.chat("How are you?", handler);

            ChatResponse response = handler.responses().get(0);

            assertThat(response.aiMessage().text()).isEqualTo("HOW ARE YOU?");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
        }
    }

    @Test
    void should_forward_raw_event_callback() {
        Object rawEvent = "provider.raw.event";

        StreamingChatModel model = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                handler.onUnmappedRawEvent(rawEvent);
                handler.onCompleteResponse(
                        ChatResponse.builder().aiMessage(new AiMessage("done")).build());
            }
        };

        CollectorResponseHandler handler = new CollectorResponseHandler();
        model.chat("search", handler);

        assertThat(handler.rawEvents()).containsExactly(rawEvent);
    }

    @Test
    void should_not_lose_provider_specific_parameters_when_default_request_parameters_are_not_overridden() {

        // given
        AtomicReference<ChatRequest> receivedRequest = new AtomicReference<>();
        StreamingChatModel model = new StreamingChatModel() {

            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                receivedRequest.set(chatRequest);
                handler.onCompleteResponse(
                        ChatResponse.builder().aiMessage(new AiMessage("Hi")).build());
            }
        };

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(new UserMessage("Hello"))
                .parameters(CustomChatRequestParameters.builder()
                        .temperature(0.7)
                        .customParameter("custom-value")
                        .build())
                .build();

        // when
        model.chat(chatRequest, new CollectorResponseHandler());

        // then
        ChatRequestParameters parameters = receivedRequest.get().parameters();
        assertThat(parameters).isInstanceOf(CustomChatRequestParameters.class);
        assertThat(((CustomChatRequestParameters) parameters).customParameter()).isEqualTo("custom-value");
        assertThat(parameters.temperature()).isEqualTo(0.7);
    }

    @Test
    void publisher_should_signal_an_invalid_request_to_the_subscriber_instead_of_throwing() {

        ChatModelListener listener = mock(ChatModelListener.class);
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(listener);

        List<Object> withoutMessages = signals(model.chat(List.<ChatMessage>of()));
        List<Object> withNoMessageArray = signals(model.chat(new ChatMessage[0]));
        List<Object> withNullRequest = signals(model.chat((ChatRequest) null));
        List<Object> withNullUserMessage = signals(model.chat((String) null));

        for (List<Object> signals : List.of(withoutMessages, withNoMessageArray, withNullRequest, withNullUserMessage)) {
            assertThat(signals).hasSize(2);
            assertThat(signals.get(0)).isEqualTo("onSubscribe");
            assertThat(signals.get(1)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat((Throwable) withNullRequest.get(1)).hasMessage("request cannot be null");
        verifyNoInteractions(listener);
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void publisher_should_signal_a_failure_to_prepare_the_request_to_the_subscriber() {

        ChatModelListener listener = mock(ChatModelListener.class);
        StreamingChatModelMock model = spy(StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(listener));
        IllegalStateException failure = new IllegalStateException("cannot merge default parameters");
        doThrow(failure).when(model).defaultRequestParameters();

        List<Object> signals = signals(
                model.chat(ChatRequest.builder().messages(UserMessage.from("Hello")).build()));

        assertThat(signals).containsExactly("onSubscribe", failure);
        verifyNoInteractions(listener);
    }

    @Test
    void handler_chat_should_report_an_invalid_request_to_on_error_instead_of_throwing() {

        ChatModelListener listener = mock(ChatModelListener.class);
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(listener);

        ErrorCollectingHandler withoutMessages = new ErrorCollectingHandler();
        ErrorCollectingHandler withNullRequest = new ErrorCollectingHandler();
        ErrorCollectingHandler withNullUserMessage = new ErrorCollectingHandler();

        assertThatCode(() -> model.chat(List.<ChatMessage>of(), withoutMessages)).doesNotThrowAnyException();
        assertThatCode(() -> model.chat((ChatRequest) null, withNullRequest)).doesNotThrowAnyException();
        assertThatCode(() -> model.chat((String) null, withNullUserMessage)).doesNotThrowAnyException();

        assertThat(withoutMessages.errors).singleElement().isInstanceOf(IllegalArgumentException.class);
        assertThat(withNullRequest.errors)
                .singleElement()
                .isInstanceOf(IllegalArgumentException.class)
                .hasFieldOrPropertyWithValue("message", "request cannot be null");
        assertThat(withNullUserMessage.errors).singleElement().isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(listener);
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void handler_chat_should_notify_the_listeners_and_the_handler_once_when_the_model_throws() {

        List<String> listenerCalls = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("cannot map the request");
        StreamingChatModel model = new ThrowingStreamingChatModel(failure, listenerCalls);
        ErrorCollectingHandler handler = new ErrorCollectingHandler();

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(handler.errors).containsExactly(failure);
        assertThat(listenerCalls).containsExactly("onRequest", "onError");
    }

    @Test
    void handler_chat_should_report_an_error_thrown_by_the_model_to_on_error() {

        List<String> listenerCalls = new ArrayList<>();
        Error failure = new NoClassDefFoundError("com/example/Missing");
        StreamingChatModel model = new ThrowingStreamingChatModel(failure, listenerCalls);
        ErrorCollectingHandler handler = new ErrorCollectingHandler();

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(handler.errors).containsExactly(failure);
        assertThat(listenerCalls).containsExactly("onRequest", "onError");
    }

    @Test
    void handler_chat_should_not_report_a_second_error_when_the_handler_on_error_throws() {

        List<String> listenerCalls = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("bad request");
        StreamingChatModel model = new StreamingChatModel() {

            @Override
            public List<ChatModelListener> listeners() {
                return List.of(recordingListener(listenerCalls));
            }

            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                handler.onError(failure); // the handler's onError throws, so the exception escapes doChat
            }
        };
        List<Throwable> errors = new ArrayList<>();
        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {}

            @Override
            public void onError(Throwable error) {
                errors.add(error);
                throw new IllegalStateException("onError failed");
            }
        };

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(errors).containsExactly(failure);
        assertThat(listenerCalls).containsExactly("onRequest", "onError");
    }

    @Test
    void handler_chat_should_throw_as_before_when_there_is_no_handler() {

        List<String> listenerCalls = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("model is disabled");
        StreamingChatModel model = new ThrowingStreamingChatModel(failure, listenerCalls);

        assertThatThrownBy(() -> model.chat("Hello", null)).isSameAs(failure);
        assertThatThrownBy(() -> model.chat(List.<ChatMessage>of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(listenerCalls).containsExactly("onRequest", "onError");
    }

    private static final class ThrowingStreamingChatModel implements StreamingChatModel {

        private final Throwable failure;
        private final List<String> listenerCalls;

        ThrowingStreamingChatModel(Throwable failure, List<String> listenerCalls) {
            this.failure = failure;
            this.listenerCalls = listenerCalls;
        }

        @Override
        public List<ChatModelListener> listeners() {
            return List.of(recordingListener(listenerCalls));
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw (Error) failure;
        }
    }

    private static ChatModelListener recordingListener(List<String> calls) {
        return new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                calls.add("onRequest");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                calls.add("onError");
            }
        };
    }

    private static final class ErrorCollectingHandler implements StreamingChatResponseHandler {

        private final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {}

        @Override
        public void onError(Throwable error) {
            errors.add(error);
        }
    }

    private static <T> List<Object> signals(Publisher<T> publisher) {
        List<Object> signals = new ArrayList<>();
        publisher.subscribe(new Subscriber<T>() {

            @Override
            public void onSubscribe(Subscription subscription) {
                signals.add("onSubscribe");
            }

            @Override
            public void onNext(T item) {
                signals.add(item);
            }

            @Override
            public void onError(Throwable error) {
                signals.add(error);
            }

            @Override
            public void onComplete() {
                signals.add("onComplete");
            }
        });
        return signals;
    }
}
