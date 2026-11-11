package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the case where output guardrails are active on a streaming AI service: when
 * guardrails are present, partial responses are buffered until validation completes and then
 * replayed to the caller. The replay must reach whichever partial-response handler the caller
 * registered — both the simple {@code Consumer<String>} and the
 * {@code BiConsumer<PartialResponse, PartialResponseContext>} variant.
 */
class StreamingOutputGuardrailBufferDrainTest {

    interface StreamingAssistant {
        @dev.langchain4j.service.guardrail.OutputGuardrails(PassThroughOutputGuardrail.class)
        TokenStream chat(String message);
    }

    public static class PassThroughOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest request) {
            return success();
        }
    }

    interface StreamingAssistantWithRecordingGuardrail {
        @dev.langchain4j.service.guardrail.OutputGuardrails(RecordingOutputGuardrail.class)
        TokenStream chat(String message);
    }

    public static class RecordingOutputGuardrail implements OutputGuardrail {
        static final CountDownLatch INVOKED = new CountDownLatch(1);

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest request) {
            INVOKED.countDown();
            return success();
        }
    }

    @Test
    void should_drain_buffered_tokens_to_BiConsumer_handler_when_output_guardrails_active() throws Exception {
        StreamingChatModel model = StreamingChatModelMock.thatAlwaysStreams("Hello", " ", "world", "!");

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .build();

        List<String> received = new CopyOnWriteArrayList<>();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        assistant
                .chat("hi")
                .onPartialResponseWithContext((partial, context) -> received.add(partial.text()))
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        future.get(5, TimeUnit.SECONDS);

        assertThat(String.join("", received)).isEqualTo("Hello world!");
    }

    @Test
    void should_run_guardrails_and_drain_buffer_when_no_complete_response_handler_registered() throws Exception {
        StreamingChatModel model = StreamingChatModelMock.thatAlwaysStreams("Hello", " ", "world", "!");

        StreamingAssistantWithRecordingGuardrail assistant = AiServices.builder(
                        StreamingAssistantWithRecordingGuardrail.class)
                .streamingChatModel(model)
                .build();

        List<String> received = new CopyOnWriteArrayList<>();

        // onCompleteResponse is optional: validateConfiguration only rejects registering it
        // more than once. Omitting it must not skip the output guardrail or swallow the
        // buffered tokens.
        assistant
                .chat("hi")
                .onPartialResponse(received::add)
                .onError(Throwable::printStackTrace)
                .start();

        assertThat(RecordingOutputGuardrail.INVOKED.await(5, TimeUnit.SECONDS))
                .as("output guardrail must run even without a complete-response handler")
                .isTrue();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (received.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(String.join("", received)).isEqualTo("Hello world!");
    }

    @Test
    void should_drain_buffered_tokens_to_Consumer_handler_when_output_guardrails_active() throws Exception {
        StreamingChatModel model = StreamingChatModelMock.thatAlwaysStreams("Hello", " ", "world", "!");

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .build();

        List<String> received = new CopyOnWriteArrayList<>();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        assistant
                .chat("hi")
                .onPartialResponse(received::add)
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        future.get(5, TimeUnit.SECONDS);

        assertThat(String.join("", received)).isEqualTo("Hello world!");
    }
}
