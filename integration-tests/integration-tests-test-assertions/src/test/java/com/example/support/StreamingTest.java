package com.example.support;

import static com.example.support.Bookings.bookings;
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static dev.langchain4j.test.service.AiServiceCapture.captureExpectingFailure;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.test.mock.StreamingChatModelMock;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.test.service.AiServiceInvocation;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Streaming AI Service methods: TokenStream and Flow.Publisher.
 */
@Timeout(30)
class StreamingTest {

    private static final String STREAM_START_GRACE_PROPERTY = "langchain4j.test.capture.stream-start-grace-millis";

    ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    StreamingCustomerSupportAgent agent(StreamingChatModel model) {
        return CustomerSupport.streamingCustomerSupportAgent(model, bookings());
    }

    @Test
    void should_fail_when_token_stream_is_neither_started_nor_returned() {
        StreamingCustomerSupportAgent agent = agent(StreamingChatModelMock.thatAlwaysStreams("Hello"));

        assertThatThrownBy(() -> capture(() -> {
                    agent.chat("Hi").onPartialResponse(token -> {});
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "The TokenStream returned by com.example.support.StreamingCustomerSupportAgent.chat was not started.")
                .hasMessageContaining("call start() in the lambda");
    }

    @Test
    void should_wait_for_token_stream_started_on_another_thread() {
        StreamingCustomerSupportAgent agent = agent(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo"));

        AiServiceInvocation invocation = capture(() -> {
                    TokenStream tokenStream = agent.chat("Hi");
                    executor.execute(() -> tokenStream
                            .onPartialResponse(token -> {})
                            .onError(error -> {})
                            .start());
                })
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("Hello");
    }

    @Test
    void should_fail_when_token_stream_is_started_on_another_thread_too_late() {
        StreamingCustomerSupportAgent agent = agent(StreamingChatModelMock.thatAlwaysStreams("Hello"));
        CountDownLatch captureFailed = new CountDownLatch(1);
        CountDownLatch streamCompleted = new CountDownLatch(1);

        // capture(...) waits 2 seconds by default for a TokenStream to be started: shortened here
        String previous = System.getProperty(STREAM_START_GRACE_PROPERTY);
        System.setProperty(STREAM_START_GRACE_PROPERTY, "100");
        try {
            assertThatThrownBy(() -> capture(() -> {
                        TokenStream tokenStream = agent.chat("Hi");
                        executor.execute(() -> {
                            await(captureFailed);
                            tokenStream
                                    .onPartialResponse(token -> {})
                                    .onCompleteResponse(response -> streamCompleted.countDown())
                                    .onError(error -> streamCompleted.countDown())
                                    .start();
                        });
                    }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("was not started");
        } finally {
            restore(STREAM_START_GRACE_PROPERTY, previous);
        }

        // the stream starts only now, and ends before the test does
        captureFailed.countDown();
        await(streamCompleted);
    }

    @Test
    void should_capture_cancelled_token_stream() {
        StreamingCustomerSupportAgent agent =
                agent(StreamingChatModelMock.thatAlwaysStreams("Hello, this is a long answer"));

        // a cancelled invocation does not make capture(...) throw
        AiServiceInvocation invocation = capture(() -> agent.chat("Hi")
                        .onPartialResponseWithContext((partialResponse, context) -> context.streamingHandle().cancel())
                        .onError(error -> {})
                        .start())
                .invocation();

        assertThat(invocation.isCancelled()).isTrue();
        assertThat(invocation.error()).isNull();
        assertThatThrownBy(invocation::outputText)
                .isInstanceOf(AssertionError.class)
                .hasMessage("The AI Service invocation was cancelled, so it has no output");
        assertThatThrownBy(invocation::output)
                .hasMessage("The AI Service invocation was cancelled, so it has no output");
        assertThatThrownBy(() -> assertThat(invocation).hasNoToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessage("[StreamingCustomerSupportAgent.chat] Expected the invocation to succeed, but it was cancelled");
        assertThatThrownBy(() -> assertThat(invocation).hasFailedWith(RuntimeException.class))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[StreamingCustomerSupportAgent.chat] Expected the invocation to fail with <java.lang.RuntimeException>, but it did not fail (it was cancelled)");
    }

    @Test
    void should_rethrow_failure_of_token_stream_also_when_passed_to_on_error() {
        StreamingCustomerSupportAgent agent =
                agent(StreamingChatModelMock.thatAlwaysThrows(new RuntimeException("Stream broken")));
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        assertThatThrownBy(() -> capture(() -> agent.chat("Hi").onPartialResponse(token -> {}).onError(errors::add).start()))
                .hasMessageContaining("Stream broken");
        assertThat(errors).singleElement().satisfies(error -> assertThat(error).hasMessageContaining("Stream broken"));
    }

    @Test
    void should_return_failed_token_stream_invocation_when_failure_is_expected() {
        StreamingCustomerSupportAgent agent =
                agent(StreamingChatModelMock.thatAlwaysThrows(new RuntimeException("Stream broken")));

        AiServiceInvocation invocation =
                captureExpectingFailure(() -> agent.chat("Hi").ignoreErrors().start()).invocation();

        assertThat(invocation).hasFailedWithMessageContaining("Stream broken");
    }

    @Test
    void should_return_failed_publisher_invocation_when_failure_is_expected() {
        StreamingCustomerSupportAgent agent =
                agent(StreamingChatModelMock.thatAlwaysThrows(new RuntimeException("Stream broken")));

        assertThatThrownBy(() -> capture(() -> agent.chatReactive("Hi"))).hasMessageContaining("Stream broken");

        AiServiceInvocation invocation = captureExpectingFailure(() -> agent.chatReactive("Hi")).invocation();
        assertThat(invocation).hasFailedWithMessageContaining("Stream broken");
    }

    @Test
    void should_capture_failure_of_streaming_model_that_throws_from_chat_when_started_by_captured_code() {
        StreamingCustomerSupportAgent agent = agent(
                StreamingChatModelMock.thatAlwaysThrows(new RuntimeException("Cannot connect")).throwingFromChat());

        AiServiceInvocation invocation = captureExpectingFailure(() -> agent.chat("Hi")
                        .onPartialResponse(token -> {})
                        .onError(error -> {})
                        .start())
                .invocation();

        assertThat(invocation).hasFailedWithMessageContaining("Cannot connect");
    }

    @Test
    void should_capture_several_token_streams() {
        StreamingCustomerSupportAgent agent = agent(StreamingChatModelMock.thatStreams(
                List.of(AiMessage.from("First"),
                        AiMessage.from("Second"))));

        List<AiServiceInvocation> invocations = capture(() -> {
                    agent.chat("One").onPartialResponse(token -> {}).onError(error -> {}).start();
                    agent.chat("Two").onPartialResponse(token -> {}).onError(error -> {}).start();
                })
                .invocations();

        assertThat(invocations).extracting(AiServiceInvocation::outputText).containsExactlyInAnyOrder("First", "Second");
    }

    @Test
    void should_not_start_again_a_returned_token_stream_that_was_already_started() {
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams("Hello");
        StreamingCustomerSupportAgent agent = agent(model);

        AiServiceInvocation invocation = capture(() -> {
                    TokenStream tokenStream = agent.chat("Hi").onPartialResponse(token -> {}).onError(error -> {});
                    tokenStream.start();
                    return tokenStream;
                })
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("Hello");
        assertThat(model.requests()).hasSize(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void restore(String property, String previous) {
        if (previous == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, previous);
        }
    }
}
