package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.service.AiServiceStreamingEvent.FinalResponseEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.IntermediateResponseEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.PartialResponseEvent;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Functional tests for output guardrails on the reactive {@link Flow.Publisher} AI Service path. When output
 * guardrails are configured the final answer may be rewritten by a guardrail, so the streamed partial responses
 * are not authoritative: no {@link PartialResponseEvent} is emitted, and only the (possibly rewritten)
 * {@link FinalResponseEvent} carries the answer (consistent with the {@code Publisher<String>} path). Guardrails —
 * and any reprompt round-trips — run off the model-delivery thread; the non-blocking guarantee itself is covered by
 * {@link AiServicesNonBlockingTest}.
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

    interface PassingStringStreamer {
        @OutputGuardrails(PassingOutputGuardrail.class)
        Flow.Publisher<String> chat(String message);
    }

    interface RewritingStringStreamer {
        @OutputGuardrails(RewritingOutputGuardrail.class)
        Flow.Publisher<String> chat(String message);
    }

    public static class PassingOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    public static class RewritingOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return successWith("rewritten by guardrail");
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    public static class FatalOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return fatal("output rejected");
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    @Test
    void passing_output_guardrail_emits_no_partials_and_emits_the_final_response() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello"));

        PassingEventStreamer assistant = AiServices.builder(PassingEventStreamer.class)
                .streamingChatModel(model)
                .build();

        Collected collected = collect(assistant.chat("Hi"));

        assertThat(collected.error).isNull();
        // With output guardrails configured, no partials are emitted (they cannot be reconciled with a possibly
        // rewritten final answer); only the FinalResponseEvent carries the answer.
        assertThat(collected.items).noneMatch(e -> e instanceof PartialResponseEvent);
        assertThat(finalText(collected)).isEqualTo("Hello");
        assertThat(collected.items.get(collected.items.size() - 1)).isInstanceOf(FinalResponseEvent.class);
    }

    @Test
    void rewriting_output_guardrail_emits_no_partials_only_the_rewritten_final_response() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello"));

        RewritingEventStreamer assistant = AiServices.builder(RewritingEventStreamer.class)
                .streamingChatModel(model)
                .build();

        Collected collected = collect(assistant.chat("Hi"));

        assertThat(collected.error).isNull();
        // No stale partials are emitted, so concatenating partials can never contradict the rewritten
        // FinalResponseEvent - only the rewritten final answer is surfaced.
        assertThat(collected.items).noneMatch(e -> e instanceof PartialResponseEvent);
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
        // A failed validation must not leak a final response, and (as always under output guardrails) no partials
        // are emitted.
        assertThat(collected.items).noneMatch(e -> e instanceof FinalResponseEvent);
        assertThat(collected.items).noneMatch(e -> e instanceof PartialResponseEvent);
    }

    @Test
    void string_publisher_emits_the_rewritten_final_text_from_an_output_guardrail() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello"));

        RewritingStringStreamer assistant = AiServices.builder(RewritingStringStreamer.class)
                .streamingChatModel(model)
                .build();

        List<String> chunks = collectStrings(assistant.chat("Hi"));

        // With an output guardrail, the individual partials are no longer authoritative (the guardrail may rewrite
        // the answer), so the String stream must reflect the rewritten final text, not the original "Hello" tokens.
        assertThat(String.join("", chunks)).isEqualTo("rewritten by guardrail");
    }

    @Test
    void string_publisher_emits_the_final_text_when_a_passing_output_guardrail_does_not_rewrite() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello"));

        PassingStringStreamer assistant = AiServices.builder(PassingStringStreamer.class)
                .streamingChatModel(model)
                .build();

        List<String> chunks = collectStrings(assistant.chat("Hi"));

        assertThat(String.join("", chunks)).isEqualTo("Hello");
    }

    public static class RepromptOnBadOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            if (responseFromLLM.text().contains("bad")) {
                return reprompt("unacceptable answer", "Please give a good answer");
            }
            return success();
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    static class WeatherTool {
        final AtomicInteger calls = new AtomicInteger();

        @Tool
        String weather(String city) {
            calls.incrementAndGet();
            return "sunny in " + city;
        }
    }

    interface ToolAwareRepromptEventStreamer {
        @OutputGuardrails(RepromptOnBadOutputGuardrail.class)
        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    @Test
    void reactive_output_guardrail_reprompt_resolves_tools_before_revalidating() throws Exception {
        // The first answer is rejected; the reprompt asks the (streaming) model again and it responds with a tool
        // call. The tool-aware reprompt executor must resolve that tool — re-calling the streaming model via the
        // async tool loop — so the guardrail only ever sees the final textual answer.
        WeatherTool tool = new WeatherTool();
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from("bad answer"),
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("weather")
                        .arguments("{\"arg0\": \"Paris\"}")
                        .build()),
                AiMessage.from("good answer"));

        ToolAwareRepromptEventStreamer assistant = AiServices.builder(ToolAwareRepromptEventStreamer.class)
                .streamingChatModel(model)
                .tools(tool)
                .build();

        Collected collected = collect(assistant.chat("What is the weather?"));

        assertThat(collected.error).isNull();
        assertThat(finalText(collected)).isEqualTo("good answer");
        assertThat(tool.calls).hasValue(1);
    }

    @Test
    void output_guardrail_emits_no_partials_and_carries_intermediate_text_via_the_intermediate_event()
            throws Exception {
        // With output guardrails, no PartialResponseEvents are emitted at all - neither the tool-calling
        // (intermediate) round's preamble text nor the final round's answer is streamed as partials. The
        // intermediate text remains observable via its IntermediateResponseEvent, and the answer via the
        // FinalResponseEvent.
        WeatherTool tool = new WeatherTool();
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.builder()
                        .text("Let me check. ")
                        .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                .id("1")
                                .name("weather")
                                .arguments("{\"arg0\": \"Paris\"}")
                                .build()))
                        .build(),
                AiMessage.from("It is sunny in Paris."));

        PassingEventStreamer assistant = AiServices.builder(PassingEventStreamer.class)
                .streamingChatModel(model)
                .tools(tool)
                .build();

        Collected collected = collect(assistant.chat("What is the weather in Paris?"));

        assertThat(collected.error).isNull();
        assertThat(tool.calls).hasValue(1);
        // No partials under output guardrails - neither the intermediate preamble nor the final answer is streamed.
        assertThat(collected.items).noneMatch(e -> e instanceof PartialResponseEvent);
        assertThat(finalText(collected)).isEqualTo("It is sunny in Paris.");
        // The intermediate round's preamble text is not lost — it is carried by the IntermediateResponseEvent.
        assertThat(intermediateText(collected)).isEqualTo("Let me check. ");
        assertThat(collected.items.get(collected.items.size() - 1)).isInstanceOf(FinalResponseEvent.class);
    }

    private static String intermediateText(Collected collected) {
        return collected.items.stream()
                .filter(e -> e instanceof IntermediateResponseEvent)
                .map(e -> ((IntermediateResponseEvent) e).chatResponse().aiMessage().text())
                .findFirst()
                .orElse(null);
    }

    private static String finalText(Collected collected) {
        return collected.items.stream()
                .filter(e -> e instanceof FinalResponseEvent)
                .map(e -> ((FinalResponseEvent) e).chatResponse().aiMessage().text())
                .findFirst()
                .orElse(null);
    }

    private static List<String> collectStrings(Flow.Publisher<String> publisher) throws InterruptedException {
        List<String> items = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
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
        assertThat(error.get()).isNull();
        return items;
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
