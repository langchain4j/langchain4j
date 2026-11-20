package dev.langchain4j.model.anthropic;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.model.anthropic.internal.api.AnthropicStreamingException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that an Anthropic stream always delivers exactly one terminal signal ({@code onCompleteResponse} or
 * {@code onError}), whatever way it ends: a regular {@code message_stop}, an {@code error} event, or an HTTP body that
 * ends before {@code message_stop} (e.g. cut by a proxy or the network). It also verifies that a stream cancelled by
 * the caller does not report an error.
 * <p>
 * A local {@link HttpServer} replays canned SSE streams, so no API key or network access is needed. Both the
 * handler-based path ({@link StreamingChatModel#chat(ChatRequest, StreamingChatResponseHandler)}) and the reactive
 * publisher path ({@link StreamingChatModel#chat(ChatRequest)}) are covered.
 */
class AnthropicStreamingChatModelTerminationTest {

    private static final String MESSAGE_START = event(
            "message_start",
            "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
                    + "\"content\":[],\"model\":\"claude-haiku-4-5-20251001\",\"stop_reason\":null,"
                    + "\"usage\":{\"input_tokens\":5,\"output_tokens\":0}}}");
    private static final String CONTENT_BLOCK_START = textBlockStart(0);
    private static final String CONTENT_BLOCK_DELTA = textDelta("Hi");
    private static final String CONTENT_BLOCK_STOP =
            event("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}");
    private static final String MESSAGE_DELTA = event(
            "message_delta",
            "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":2}}");
    private static final String MESSAGE_STOP = event("message_stop", "{\"type\":\"message_stop\"}");
    private static final String OVERLOADED_ERROR =
            event("error", "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}");

    private static final String STREAM_WITHOUT_MESSAGE_STOP =
            MESSAGE_START + CONTENT_BLOCK_START + CONTENT_BLOCK_DELTA + CONTENT_BLOCK_STOP + MESSAGE_DELTA;
    private static final String COMPLETE_STREAM = STREAM_WITHOUT_MESSAGE_STOP + MESSAGE_STOP;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    // How long a stream must stay quiet after its terminal signal to prove that no second signal follows.
    private static final Duration QUIET_PERIOD = Duration.ofMillis(500);

    private HttpServer server;
    private String baseUrl;

    private volatile ResponseWriter responseWriter;
    private final AtomicBoolean serverObservedDisconnect = new AtomicBoolean();

    @BeforeEach
    void startServer() throws Exception {
        // Bind to the loopback address rather than the wildcard: "localhost" can resolve to both ::1 and 127.0.0.1,
        // and the client may then dial an address the server is not reachable on.
        InetAddress loopback = InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        server.createContext("/v1/messages", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0); // 0 => chunked transfer, unknown length
            try (OutputStream os = exchange.getResponseBody()) {
                responseWriter.writeTo(os);
            } catch (IOException e) {
                // The client tore down the connection mid-stream.
                serverObservedDisconnect.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        baseUrl = "http://" + loopback.getHostAddress() + ":"
                + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void should_complete_exactly_once_when_stream_ends_with_message_stop() {

        // given
        respondWith(COMPLETE_STREAM);
        RecordingHandler handler = new RecordingHandler();

        // when
        model().chat(request(), handler);

        // then
        awaitExactlyOneTerminalSignal(handler);
        assertThat(handler.errors).isEmpty();
        assertThat(handler.completeResponses).hasSize(1);
        assertThat(handler.completeResponses.get(0).aiMessage().text()).isEqualTo("Hi");
    }

    @Test
    void should_fail_with_retriable_exception_when_stream_ends_without_message_stop() {

        // given
        respondWith(STREAM_WITHOUT_MESSAGE_STOP);
        RecordingHandler handler = new RecordingHandler();

        // when
        model().chat(request(), handler);

        // then
        awaitExactlyOneTerminalSignal(handler);
        assertThat(handler.completeResponses).isEmpty();
        assertThat(handler.partialResponses).containsExactly("Hi");
        assertThat(handler.errors).hasSize(1);
        assertThat(handler.errors.get(0))
                .isExactlyInstanceOf(RetriableException.class)
                .hasMessage(
                        "Anthropic stream ended before the message_stop event (stop_reason 'end_turn' was received)");
    }

    @Test
    void should_fail_when_stream_ends_before_message_delta() {

        // given
        respondWith(MESSAGE_START + CONTENT_BLOCK_START + CONTENT_BLOCK_DELTA);
        RecordingHandler handler = new RecordingHandler();

        // when
        model().chat(request(), handler);

        // then
        awaitExactlyOneTerminalSignal(handler);
        assertThat(handler.completeResponses).isEmpty();
        assertThat(handler.errors).hasSize(1);
        assertThat(handler.errors.get(0))
                .isExactlyInstanceOf(RetriableException.class)
                .hasMessage("Anthropic stream ended before the message_stop event");
    }

    @Test
    void should_fail_when_response_body_is_empty() {

        // given
        respondWith("");
        RecordingHandler handler = new RecordingHandler();

        // when
        model().chat(request(), handler);

        // then
        awaitExactlyOneTerminalSignal(handler);
        assertThat(handler.completeResponses).isEmpty();
        assertThat(handler.errors).hasSize(1);
        assertThat(handler.errors.get(0))
                .isExactlyInstanceOf(RetriableException.class)
                .hasMessage("Anthropic stream ended before the message_stop event");
    }

    @Test
    void should_fail_when_message_stop_cannot_be_parsed() {

        // given
        String malformedMessageStop = event("message_stop", "{\"type\":\"message_stop\"");
        respondWith(STREAM_WITHOUT_MESSAGE_STOP + malformedMessageStop);
        RecordingHandler handler = new RecordingHandler();

        // when
        model().chat(request(), handler);

        // then
        awaitExactlyOneTerminalSignal(handler);
        assertThat(handler.completeResponses).isEmpty();
        assertThat(handler.errors).hasSize(1);
        assertThat(handler.errors.get(0)).isExactlyInstanceOf(RetriableException.class);
    }

    @Test
    void should_report_error_event_exactly_once() {

        // given
        respondWith(MESSAGE_START + CONTENT_BLOCK_START + CONTENT_BLOCK_DELTA + OVERLOADED_ERROR);
        RecordingHandler handler = new RecordingHandler();

        // when
        model().chat(request(), handler);

        // then
        awaitExactlyOneTerminalSignal(handler);
        assertThat(handler.completeResponses).isEmpty();
        assertThat(handler.errors).hasSize(1);
        assertThat(handler.errors.get(0))
                .isExactlyInstanceOf(AnthropicStreamingException.class)
                .hasMessage("Overloaded");
        assertThat(((AnthropicStreamingException) handler.errors.get(0)).type()).isEqualTo("overloaded_error");
    }

    @Test
    void should_ignore_events_received_after_message_stop() {

        // given
        // A new content block is opened after message_stop: a delta for the already stopped block 0 would be
        // dropped anyway (its type is forgotten on content_block_stop), so it would not exercise the terminal guard.
        String lateTextBlock = textBlockStart(1) + textDelta(1, " ignored");
        respondWith(COMPLETE_STREAM + lateTextBlock + OVERLOADED_ERROR);
        RecordingHandler handler = new RecordingHandler();

        // when
        model().chat(request(), handler);

        // then
        awaitExactlyOneTerminalSignal(handler);
        assertThat(handler.errors).isEmpty();
        assertThat(handler.completeResponses).hasSize(1);
        assertThat(handler.partialResponses).containsExactly("Hi");
    }

    @Test
    void should_not_report_error_when_stream_is_cancelled_by_caller() {

        // given
        int partialResponsesBeforeCancellation = 3;
        respondWith(os -> {
            write(os, MESSAGE_START + CONTENT_BLOCK_START);
            // Long enough that the body cannot be fully written before the client cancels; the test ends as soon as
            // the server observes the disconnect.
            for (int i = 0; i < 200; i++) {
                write(os, textDelta("chunk-" + i + " "));
                Thread.sleep(50);
            }
            write(os, CONTENT_BLOCK_STOP + MESSAGE_DELTA + MESSAGE_STOP);
        });

        AtomicReference<StreamingHandle> streamingHandle = new AtomicReference<>();
        RecordingHandler handler = new RecordingHandler() {

            @Override
            public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                super.onPartialResponse(partialResponse, context);
                if (partialResponses.size() == partialResponsesBeforeCancellation) {
                    streamingHandle.set(context.streamingHandle());
                    context.streamingHandle().cancel();
                }
            }
        };

        // when
        model().chat(request(), handler);

        // then
        await().atMost(TIMEOUT).untilTrue(serverObservedDisconnect);
        await().during(QUIET_PERIOD).atMost(QUIET_PERIOD.plus(TIMEOUT)).until(() -> handler.terminalSignals() == 0);
        assertThat(streamingHandle.get().isCancelled()).isTrue();
        assertThat(handler.partialResponses).hasSize(partialResponsesBeforeCancellation);
    }

    @Test
    void should_pass_exception_thrown_by_onCompleteResponse_to_onError() {

        // given
        respondWith(COMPLETE_STREAM);
        RuntimeException userException = new RuntimeException("thrown by onCompleteResponse");
        RecordingHandler handler = new RecordingHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                super.onCompleteResponse(completeResponse);
                throw userException;
            }
        };

        // when
        model().chat(request(), handler);

        // then the existing contract is kept: an exception thrown by the handler is passed to its onError()
        await().atMost(TIMEOUT).until(() -> handler.errors.size() == 1);
        await().during(QUIET_PERIOD).atMost(QUIET_PERIOD.plus(TIMEOUT)).until(() -> handler.terminalSignals() == 2);
        assertThat(handler.completeResponses).hasSize(1);
        assertThat(handler.errors).containsExactly(userException);
    }

    @Test
    void should_fail_publisher_when_stream_ends_without_message_stop() {

        // given
        respondWith(STREAM_WITHOUT_MESSAGE_STOP);
        RecordingSubscriber subscriber = new RecordingSubscriber();

        // when
        model().chat(request()).subscribe(subscriber);

        // then
        awaitExactlyOneTerminalSignal(subscriber);
        assertThat(subscriber.completions).hasValue(0);
        assertThat(subscriber.events).noneMatch(event -> event instanceof CompleteResponse);
        assertThat(subscriber.errors).hasSize(1);
        assertThat(subscriber.errors.get(0))
                .isExactlyInstanceOf(RetriableException.class)
                .hasMessageContaining("message_stop");
    }

    @Test
    void should_fail_publisher_exactly_once_on_error_event() {

        // given
        respondWith(MESSAGE_START + CONTENT_BLOCK_START + CONTENT_BLOCK_DELTA + OVERLOADED_ERROR);
        RecordingSubscriber subscriber = new RecordingSubscriber();

        // when
        model().chat(request()).subscribe(subscriber);

        // then
        awaitExactlyOneTerminalSignal(subscriber);
        assertThat(subscriber.completions).hasValue(0);
        assertThat(subscriber.errors).hasSize(1);
        assertThat(subscriber.errors.get(0)).isExactlyInstanceOf(AnthropicStreamingException.class);
    }

    @Test
    void should_complete_publisher_exactly_once_when_stream_ends_with_message_stop() {

        // given
        respondWith(COMPLETE_STREAM);
        RecordingSubscriber subscriber = new RecordingSubscriber();

        // when
        model().chat(request()).subscribe(subscriber);

        // then
        awaitExactlyOneTerminalSignal(subscriber);
        assertThat(subscriber.errors).isEmpty();
        assertThat(subscriber.completions).hasValue(1);
        assertThat(subscriber.events.get(subscriber.events.size() - 1)).isInstanceOf(CompleteResponse.class);
    }

    private StreamingChatModel model() {
        return AnthropicStreamingChatModel.builder()
                .apiKey("dummy-key")
                .baseUrl(baseUrl)
                .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(10)
                .build();
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("Hi")).build();
    }

    private void respondWith(String body) {
        respondWith(os -> write(os, body));
    }

    private void respondWith(ResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    private static void awaitExactlyOneTerminalSignal(TerminalSignalCounter counter) {
        await().atMost(TIMEOUT).until(() -> counter.terminalSignals() > 0);
        await().during(QUIET_PERIOD).atMost(QUIET_PERIOD.plus(TIMEOUT)).until(() -> counter.terminalSignals() == 1);
    }

    private static String event(String name, String data) {
        return "event: " + name + "\ndata: " + data + "\n\n";
    }

    private static String textBlockStart(int index) {
        return event(
                "content_block_start",
                "{\"type\":\"content_block_start\",\"index\":" + index
                        + ",\"content_block\":{\"type\":\"text\",\"text\":\"\"}}");
    }

    private static String textDelta(String text) {
        return textDelta(0, text);
    }

    private static String textDelta(int index, String text) {
        return event(
                "content_block_delta",
                "{\"type\":\"content_block_delta\",\"index\":" + index
                        + ",\"delta\":{\"type\":\"text_delta\",\"text\":\"" + text + "\"}}");
    }

    private static void write(OutputStream os, String data) throws IOException {
        os.write(data.getBytes(UTF_8));
        os.flush();
    }

    @FunctionalInterface
    private interface ResponseWriter {

        void writeTo(OutputStream os) throws IOException, InterruptedException;
    }

    private interface TerminalSignalCounter {

        int terminalSignals();
    }

    private static class RecordingHandler implements StreamingChatResponseHandler, TerminalSignalCounter {

        final List<String> partialResponses = new CopyOnWriteArrayList<>();
        final List<ChatResponse> completeResponses = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();

        @Override
        public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
            partialResponses.add(partialResponse.text());
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            completeResponses.add(completeResponse);
        }

        @Override
        public void onError(Throwable error) {
            errors.add(error);
        }

        @Override
        public int terminalSignals() {
            return completeResponses.size() + errors.size();
        }
    }

    private static class RecordingSubscriber
            implements Flow.Subscriber<ChatModelStreamingEvent>, TerminalSignalCounter {

        final List<ChatModelStreamingEvent> events = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final AtomicInteger completions = new AtomicInteger();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ChatModelStreamingEvent event) {
            events.add(event);
        }

        @Override
        public void onError(Throwable throwable) {
            errors.add(throwable);
        }

        @Override
        public void onComplete() {
            completions.incrementAndGet();
        }

        @Override
        public int terminalSignals() {
            return errors.size() + completions.get();
        }
    }
}
