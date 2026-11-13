package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.CustomChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.LambdaStreamingResponseHandler.onPartialResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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
        StreamingChatModel model =
                new StreamingChatModelMock(failure).throwingFromChat().withListeners(recordingListener(listenerCalls));
        ErrorCollectingHandler handler = new ErrorCollectingHandler();

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(handler.errors).containsExactly(failure);
        assertThat(listenerCalls).containsExactly("onRequest", "onError");
    }

    @Test
    void handler_chat_should_report_an_error_thrown_by_the_model_to_on_error() {

        List<String> listenerCalls = new ArrayList<>();
        Error failure = new NoClassDefFoundError("com/example/Missing");
        StreamingChatModelMock model = spy(
                StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(recordingListener(listenerCalls)));
        doThrow(failure).when(model).doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        ErrorCollectingHandler handler = new ErrorCollectingHandler();

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(handler.errors).containsExactly(failure);
        assertThat(listenerCalls).containsExactly("onRequest", "onError");
    }

    @Test
    void handler_chat_should_throw_what_on_error_throws_without_reporting_a_second_error() {

        List<String> listenerCalls = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("bad request");
        StreamingChatModelMock model = spy(
                StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(recordingListener(listenerCalls)));
        doAnswer(invocation -> {
                    // the handler's onError throws, so the exception escapes doChat
                    invocation.getArgument(1, StreamingChatResponseHandler.class).onError(failure);
                    return null;
                })
                .when(model)
                .doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        RuntimeException onErrorFailure = new IllegalStateException("onError failed");
        ThrowingOnErrorHandler handler = new ThrowingOnErrorHandler(onErrorFailure);

        assertThatThrownBy(() -> model.chat("Hello", handler)).isSameAs(onErrorFailure);

        assertThat(handler.errors).containsExactly(failure);
        assertThat(listenerCalls).containsExactly("onRequest", "onError");
    }

    @Test
    void handler_chat_should_throw_what_on_error_throws_for_an_invalid_request() {

        RuntimeException onErrorFailure = new IllegalStateException("onError failed");
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams("Hi");
        ThrowingOnErrorHandler withoutMessages = new ThrowingOnErrorHandler(onErrorFailure);
        ThrowingOnErrorHandler withNullRequest = new ThrowingOnErrorHandler(onErrorFailure);
        ThrowingOnErrorHandler withNullUserMessage = new ThrowingOnErrorHandler(onErrorFailure);

        assertThatThrownBy(() -> model.chat(List.<ChatMessage>of(), withoutMessages)).isSameAs(onErrorFailure);
        assertThatThrownBy(() -> model.chat((ChatRequest) null, withNullRequest)).isSameAs(onErrorFailure);
        assertThatThrownBy(() -> model.chat((String) null, withNullUserMessage)).isSameAs(onErrorFailure);

        assertThat(withoutMessages.errors).singleElement().isInstanceOf(IllegalArgumentException.class);
        assertThat(withNullRequest.errors).singleElement().isInstanceOf(IllegalArgumentException.class);
        assertThat(withNullUserMessage.errors).singleElement().isInstanceOf(IllegalArgumentException.class);
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void handler_chat_should_throw_an_error_thrown_by_on_error() {

        Error onErrorFailure = new AssertionError("unexpected error");
        ThrowingOnErrorHandler handler = new ThrowingOnErrorHandler(onErrorFailure);

        assertThatThrownBy(() -> StreamingChatModelMock.thatAlwaysStreams("Hi").chat(List.<ChatMessage>of(), handler))
                .isSameAs(onErrorFailure);

        assertThat(handler.errors).singleElement().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handler_chat_should_throw_what_on_error_throws_when_the_model_throws() {

        List<String> listenerCalls = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("cannot map the request");
        StreamingChatModel model =
                new StreamingChatModelMock(failure).throwingFromChat().withListeners(recordingListener(listenerCalls));
        RuntimeException onErrorFailure = new IllegalStateException("onError failed");
        ThrowingOnErrorHandler handler = new ThrowingOnErrorHandler(onErrorFailure);

        assertThatThrownBy(() -> model.chat("Hello", handler)).isSameAs(onErrorFailure);

        assertThat(handler.errors).containsExactly(failure);
        assertThat(listenerCalls).containsExactly("onRequest", "onError");
    }

    @Test
    void handler_chat_should_make_the_shortest_lambda_handler_fail_loudly_for_an_invalid_request() {

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams("Hi");

        assertThatThrownBy(() -> model.chat(List.<ChatMessage>of(), onPartialResponse(token -> {})))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handler_chat_should_make_the_shortest_lambda_handler_throw_the_original_exception() {

        UnsupportedFeatureException unsupported = new UnsupportedFeatureException("'topK' parameter is not supported");
        StreamingChatModel model = new StreamingChatModelMock(unsupported).throwingFromChat();

        assertThatThrownBy(() -> model.chat("Hello", onPartialResponse(token -> {}))).isSameAs(unsupported);
    }

    @Test
    void handler_chat_should_only_log_what_on_error_throws_on_another_thread_before_chat_returns() throws Exception {

        List<String> listenerCalls = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("connection reset");
        StreamingChatModelMock model = spy(
                StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(recordingListener(listenerCalls)));
        AtomicReference<Throwable> reachedTheModel = new AtomicReference<>();
        doAnswer(invocation -> {
                    // like a provider, report the failure on its own thread, here before chat(...) returns
                    StreamingChatResponseHandler handler = invocation.getArgument(1);
                    Thread thread = new Thread(() -> {
                        try {
                            handler.onError(failure);
                        } catch (Throwable e) {
                            reachedTheModel.set(e);
                        }
                    });
                    thread.start();
                    thread.join(5_000);
                    return null;
                })
                .when(model)
                .doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        ThrowingOnErrorHandler handler = new ThrowingOnErrorHandler(new IllegalStateException("onError failed"));

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(reachedTheModel.get()).isNull();
        assertThat(handler.errors).containsExactly(failure);
        assertThat(listenerCalls).containsExactly("onRequest", "onError");
    }

    @Test
    void handler_chat_should_only_log_what_on_error_throws_on_another_thread_after_chat_returns() throws Exception {

        RuntimeException failure = new IllegalStateException("connection reset");
        StreamingChatModelMock model = spy(StreamingChatModelMock.thatAlwaysStreams("Hi"));
        AtomicReference<StreamingChatResponseHandler> modelHandler = new AtomicReference<>();
        doAnswer(invocation -> {
                    modelHandler.set(invocation.getArgument(1)); // the provider reports the failure later
                    return null;
                })
                .when(model)
                .doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        ThrowingOnErrorHandler handler = new ThrowingOnErrorHandler(new IllegalStateException("onError failed"));
        model.chat("Hello", handler);
        AtomicReference<Throwable> reachedTheModel = new AtomicReference<>();

        Thread thread = new Thread(() -> {
            try {
                modelHandler.get().onError(failure);
            } catch (Throwable e) {
                reachedTheModel.set(e);
            }
        });
        thread.start();
        thread.join(5_000);

        assertThat(reachedTheModel.get()).isNull();
        assertThat(handler.errors).containsExactly(failure);
    }

    @Test
    void handler_chat_should_only_log_what_on_error_throws_on_the_calling_thread_after_chat_returns() {

        RuntimeException failure = new IllegalStateException("connection reset");
        StreamingChatModelMock model = spy(StreamingChatModelMock.thatAlwaysStreams("Hi"));
        AtomicReference<StreamingChatResponseHandler> modelHandler = new AtomicReference<>();
        doAnswer(invocation -> {
                    // like a provider on an event loop: the failure is reported later, on the same thread
                    modelHandler.set(invocation.getArgument(1));
                    return null;
                })
                .when(model)
                .doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        ThrowingOnErrorHandler handler = new ThrowingOnErrorHandler(new IllegalStateException("onError failed"));
        model.chat("Hello", handler);

        assertThatCode(() -> modelHandler.get().onError(failure)).doesNotThrowAnyException();

        assertThat(handler.errors).containsExactly(failure);
    }

    @Test
    void handler_chat_should_not_report_an_error_when_the_model_throws_after_completing() {

        List<String> listenerCalls = new ArrayList<>();
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from("Hi")).build();
        StreamingChatModelMock model = spy(
                StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(recordingListener(listenerCalls)));
        doAnswer(invocation -> {
                    invocation.getArgument(1, StreamingChatResponseHandler.class).onCompleteResponse(response);
                    throw new IllegalStateException("cleanup failed");
                })
                .when(model)
                .doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        ErrorCollectingHandler handler = new ErrorCollectingHandler();

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(handler.responses).containsExactly(response);
        assertThat(handler.errors).isEmpty();
        assertThat(listenerCalls).containsExactly("onRequest", "onResponse");
    }

    @Test
    void handler_chat_should_report_a_failing_on_complete_response_when_it_throws_out_of_the_model() {

        List<String> listenerCalls = new ArrayList<>();
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from("Hi")).build();
        StreamingChatModelMock model = spy(
                StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(recordingListener(listenerCalls)));
        doAnswer(invocation -> {
                    // a model that calls the handler on the calling thread, without catching what it throws
                    invocation.getArgument(1, StreamingChatResponseHandler.class).onCompleteResponse(response);
                    return null;
                })
                .when(model)
                .doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        RuntimeException failure = new IllegalStateException("output guardrail failed");
        ErrorCollectingHandler handler = new ErrorCollectingHandler(failure);

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(handler.errors).containsExactly(failure);
        assertThat(listenerCalls).containsExactly("onRequest", "onResponse", "onError");
    }

    @Test
    void handler_chat_should_report_a_failing_on_complete_response_when_the_model_catches_it() {

        List<String> listenerCalls = new ArrayList<>();
        StreamingChatModel model = StreamingChatModelMock.thatAlwaysStreams("Hi")
                .streamingOnCallingThread()
                .withListeners(recordingListener(listenerCalls));
        RuntimeException failure = new IllegalStateException("output guardrail failed");
        ErrorCollectingHandler handler = new ErrorCollectingHandler(failure);

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(handler.errors).containsExactly(failure);
        assertThat(listenerCalls).containsExactly("onRequest", "onResponse", "onError");
    }

    @Test
    void handler_chat_should_report_every_failing_on_partial_response_that_the_model_catches() {

        List<String> listenerCalls = new ArrayList<>();
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from("Hi")).build();
        StreamingChatModelMock model = spy(
                StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(recordingListener(listenerCalls)));
        doAnswer(invocation -> {
                    // like a provider: each exception thrown by the handler is reported to its onError
                    StreamingChatResponseHandler handler = invocation.getArgument(1);
                    InternalStreamingChatResponseHandlerUtils.onPartialResponse(handler, "H");
                    InternalStreamingChatResponseHandlerUtils.onPartialResponse(handler, "i");
                    InternalStreamingChatResponseHandlerUtils.onCompleteResponse(handler, response);
                    return null;
                })
                .when(model)
                .doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        RuntimeException failure = new IllegalStateException("cannot show the token");
        ErrorCollectingHandler handler = new ErrorCollectingHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                throw failure;
            }
        };

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(handler.errors).containsExactly(failure, failure);
        assertThat(handler.responses).containsExactly(response);
        assertThat(listenerCalls).containsExactly("onRequest", "onError", "onError", "onResponse");
    }

    @Test
    void handler_chat_should_deliver_an_error_that_the_model_reports_after_the_complete_response() {

        List<String> listenerCalls = new ArrayList<>();
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from("Hi")).build();
        RuntimeException failure = new IllegalStateException("connection reset");
        StreamingChatModelMock model = spy(
                StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(recordingListener(listenerCalls)));
        doAnswer(invocation -> {
                    StreamingChatResponseHandler handler = invocation.getArgument(1);
                    handler.onCompleteResponse(response);
                    handler.onError(failure);
                    return null;
                })
                .when(model)
                .doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        ErrorCollectingHandler handler = new ErrorCollectingHandler();

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(handler.responses).containsExactly(response);
        assertThat(handler.errors).containsExactly(failure);
        assertThat(listenerCalls).containsExactly("onRequest", "onResponse", "onError");
    }

    @Test
    void handler_chat_should_report_once_a_failing_on_partial_response_that_throws_out_of_the_model() {

        List<String> listenerCalls = new ArrayList<>();
        StreamingChatModelMock model = spy(
                StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(recordingListener(listenerCalls)));
        doAnswer(invocation -> {
                    // a model that does not catch what the handler throws: the exception leaves doChat(...)
                    StreamingChatResponseHandler handler = invocation.getArgument(1);
                    handler.onPartialResponse("Hi");
                    return null;
                })
                .when(model)
                .doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        RuntimeException failure = new IllegalStateException("cannot show the token");
        List<Throwable> errors = new ArrayList<>();
        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                throw failure;
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {}

            @Override
            public void onError(Throwable error) {
                errors.add(error);
            }
        };

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(errors).containsExactly(failure);
        assertThat(listenerCalls).containsExactly("onRequest", "onError");
    }

    @Test
    void handler_chat_should_deliver_every_error_that_the_model_reports() {

        List<String> listenerCalls = new ArrayList<>();
        RuntimeException first = new IllegalStateException("first");
        RuntimeException second = new IllegalStateException("second");
        StreamingChatModelMock model = spy(
                StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(recordingListener(listenerCalls)));
        doAnswer(invocation -> {
                    StreamingChatResponseHandler handler = invocation.getArgument(1);
                    handler.onError(first);
                    handler.onError(second);
                    return null;
                })
                .when(model)
                .doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        ErrorCollectingHandler handler = new ErrorCollectingHandler();

        model.chat("Hello", handler);

        assertThat(handler.errors).containsExactly(first, second);
        assertThat(listenerCalls).containsExactly("onRequest", "onError", "onError");
    }

    @Test
    void handler_chat_should_not_report_a_second_error_when_the_model_throws_after_reporting_one() {

        List<String> listenerCalls = new ArrayList<>();
        RuntimeException reported = new IllegalStateException("bad request");
        StreamingChatModelMock model = spy(
                StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(recordingListener(listenerCalls)));
        doAnswer(invocation -> {
                    invocation.getArgument(1, StreamingChatResponseHandler.class).onError(reported);
                    throw new IllegalStateException("cleanup failed");
                })
                .when(model)
                .doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        ErrorCollectingHandler handler = new ErrorCollectingHandler();

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(handler.errors).containsExactly(reported);
        assertThat(listenerCalls).containsExactly("onRequest", "onError");
    }

    @Test
    void handler_chat_should_reject_a_null_handler_before_anything_is_sent() {

        List<String> listenerCalls = new ArrayList<>();
        StreamingChatModelMock model =
                spy(StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(recordingListener(listenerCalls)));
        ChatRequest request = ChatRequest.builder().messages(UserMessage.from("Hello")).build();

        assertThatThrownBy(() -> model.chat("Hello", null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("handler cannot be null");
        assertThatThrownBy(() -> model.chat(List.<ChatMessage>of(), null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("handler cannot be null");
        assertThatThrownBy(() -> model.chat(request, (StreamingChatResponseHandler) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("handler cannot be null");
        assertThatThrownBy(() -> model.chat(request, ChatRequestOptions.EMPTY, null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("handler cannot be null");

        assertThat(listenerCalls).isEmpty();
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void publisher_should_signal_a_null_publisher_of_the_model_to_the_subscriber() {

        List<String> listenerCalls = new ArrayList<>();
        StreamingChatModelMock model = spy(
                StreamingChatModelMock.thatAlwaysStreams("Hi").withListeners(recordingListener(listenerCalls)));
        doReturn(null).when(model).doChat(any(ChatRequest.class));

        List<Object> signals = signals(
                model.chat(ChatRequest.builder().messages(UserMessage.from("Hello")).build()));

        assertThat(signals).hasSize(2);
        assertThat(signals.get(0)).isEqualTo("onSubscribe");
        assertThat((Throwable) signals.get(1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageEndingWith(".doChat(ChatRequest) returned null");
        assertThat(listenerCalls).containsExactly("onRequest", "onError");
    }

    @Test
    void handler_chat_should_report_a_failing_provider_lookup_to_on_error_instead_of_throwing() {

        RuntimeException failure = new IllegalStateException("no provider");
        StreamingChatModelMock model = spy(StreamingChatModelMock.thatAlwaysStreams("Hi"));
        doThrow(failure).when(model).provider();
        ErrorCollectingHandler handler = new ErrorCollectingHandler();

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(handler.errors).containsExactly(failure);
    }

    @Test
    void handler_chat_should_report_the_failure_to_the_handler_even_when_a_listener_throws_an_error() {

        RuntimeException failure = new IllegalStateException("cannot map the request");
        ChatModelListener failingListener = new ChatModelListener() {

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                throw new NoClassDefFoundError("io/micrometer/Missing");
            }
        };
        StreamingChatModel model =
                new StreamingChatModelMock(failure).throwingFromChat().withListeners(failingListener);
        ErrorCollectingHandler handler = new ErrorCollectingHandler();

        assertThatCode(() -> model.chat("Hello", handler)).doesNotThrowAnyException();

        assertThat(handler.errors).containsExactly(failure);
    }

    @Test
    void handler_chat_should_restore_the_interrupt_flag_when_the_model_throws_an_interrupted_exception() {

        InterruptedException interrupted = new InterruptedException("interrupted while sending");
        StreamingChatModelMock model = spy(StreamingChatModelMock.thatAlwaysStreams("Hi"));
        doAnswer(invocation -> {
                    throw interrupted; // a sneaky throw, for example from code that does not declare it
                })
                .when(model)
                .doChat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        ErrorCollectingHandler handler = new ErrorCollectingHandler();

        try {
            model.chat("Hello", handler);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(handler.errors).containsExactly(interrupted);
        } finally {
            Thread.interrupted();
        }
    }

    private static ChatModelListener recordingListener(List<String> calls) {
        return new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                calls.add("onRequest");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                calls.add("onResponse");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                calls.add("onError");
            }
        };
    }

    private static final class ThrowingOnErrorHandler implements StreamingChatResponseHandler {

        private final List<Throwable> errors = new CopyOnWriteArrayList<>();
        private final Throwable onErrorFailure;

        ThrowingOnErrorHandler(Throwable onErrorFailure) {
            this.onErrorFailure = onErrorFailure;
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {}

        @Override
        public void onError(Throwable error) {
            errors.add(error);
            if (onErrorFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw (Error) onErrorFailure;
        }
    }

    private static class ErrorCollectingHandler implements StreamingChatResponseHandler {

        private final List<ChatResponse> responses = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();
        private final RuntimeException onCompleteResponseFailure;

        ErrorCollectingHandler() {
            this(null);
        }

        ErrorCollectingHandler(RuntimeException onCompleteResponseFailure) {
            this.onCompleteResponseFailure = onCompleteResponseFailure;
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            responses.add(completeResponse);
            if (onCompleteResponseFailure != null) {
                throw onCompleteResponseFailure;
            }
        }

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
