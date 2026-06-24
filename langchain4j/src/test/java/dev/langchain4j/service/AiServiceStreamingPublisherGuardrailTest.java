package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.service.AiServiceStreamingEvent.FinalResponseEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.PartialResponseEvent;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Functional tests for output guardrails on the reactive {@link Flow.Publisher} AI Service path. Mirrors the
 * handler-based {@code AiServiceTokenStream} buffer-then-validate behaviour: while output guardrails are
 * configured, partial responses are buffered (not streamed) until the assembled final response has passed the
 * guardrails, then the (original) partials are flushed and the (possibly rewritten) final response is emitted.
 * Guardrails — and any reprompt round-trips — run off the model-delivery thread; the
 * non-blocking guarantee itself is covered by {@link AiServicesNonBlockingTest}.
 */
class AiServiceStreamingPublisherGuardrailTest {

    interface PassingEventStreamer {
        @OutputGuardrails(PassingOutputGuardrail.class)
        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    interface RewritingEventStreamer {
        @OutputGuardrails(RewritingOutputGuardrail.class)
        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    interface FatalEventStreamer {
        @OutputGuardrails(FatalOutputGuardrail.class)
        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    public static class PassingOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }

        @Override
        public CompletionStage<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    public static class RewritingOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return successWith("rewritten by guardrail");
        }

        @Override
        public CompletionStage<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    public static class FatalOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return fatal("output rejected");
        }

        @Override
        public CompletionStage<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    @Test
    void passing_output_guardrail_flushes_buffered_partials_and_emits_final() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello"));

        PassingEventStreamer assistant = AiServices.builder(PassingEventStreamer.class)
                .streamingChatModel(model)
                .build();

        Collected collected = collect(assistant.chat("Hi"));

        assertThat(collected.error).isNull();
        assertThat(partialText(collected)).isEqualTo("Hello");
        assertThat(finalText(collected)).isEqualTo("Hello");
        // the FinalResponseEvent is emitted last, after the flushed partials
        assertThat(collected.items.get(collected.items.size() - 1)).isInstanceOf(FinalResponseEvent.class);
    }

    @Test
    void rewriting_output_guardrail_keeps_original_partials_but_rewrites_the_final_response() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello"));

        RewritingEventStreamer assistant = AiServices.builder(RewritingEventStreamer.class)
                .streamingChatModel(model)
                .build();

        Collected collected = collect(assistant.chat("Hi"));

        assertThat(collected.error).isNull();
        // The buffered partials are the original model tokens; the guardrail rewrites only the final response.
        assertThat(partialText(collected)).isEqualTo("Hello");
        assertThat(finalText(collected)).isEqualTo("rewritten by guardrail");
        assertThat(collected.items.get(collected.items.size() - 1)).isInstanceOf(FinalResponseEvent.class);
    }

    @Test
    void fatal_output_guardrail_fails_the_stream_and_emits_no_final_response() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello"));

        FatalEventStreamer assistant = AiServices.builder(FatalEventStreamer.class)
                .streamingChatModel(model)
                .build();

        Collected collected = collect(assistant.chat("Hi"));

        assertThat(collected.error).isNotNull();
        // A failed validation must not leak a final response, and the buffered partials must not be flushed.
        assertThat(collected.items).noneMatch(e -> e instanceof FinalResponseEvent);
        assertThat(collected.items).noneMatch(e -> e instanceof PartialResponseEvent);
    }

    private static String partialText(Collected collected) {
        return collected.items.stream()
                .filter(e -> e instanceof PartialResponseEvent)
                .map(e -> ((PartialResponseEvent) e).partialResponse().text())
                .reduce("", String::concat);
    }

    private static String finalText(Collected collected) {
        return collected.items.stream()
                .filter(e -> e instanceof FinalResponseEvent)
                .map(e -> ((FinalResponseEvent) e).chatResponse().aiMessage().text())
                .findFirst()
                .orElse(null);
    }

    private static Collected collect(Flow.Publisher<AiServiceStreamingEvent> publisher) throws InterruptedException {
        List<AiServiceStreamingEvent> items = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AiServiceStreamingEvent item) {
                items.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        assertThat(latch.await(10, TimeUnit.SECONDS))
                .as("stream terminated within 10s")
                .isTrue();
        return new Collected(items, error.get());
    }

    private record Collected(List<AiServiceStreamingEvent> items, Throwable error) {}
}
