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
