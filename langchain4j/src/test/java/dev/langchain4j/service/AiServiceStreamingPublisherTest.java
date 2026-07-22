package dev.langchain4j.service;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import static dev.langchain4j.service.AsyncTestTimeouts.TIMEOUT_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.observability.api.event.CompensationReason;
import dev.langchain4j.observability.api.event.ToolCompensatedEvent;
import dev.langchain4j.observability.api.listener.ToolCompensatedEventListener;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.RawStreamingEvent;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.AiServiceStreamingEvent.AfterToolExecutionEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.BeforeToolExecutionEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.FinalResponseEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.IntermediateResponseEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.PartialResponseEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.RawEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.RetrievedContentsEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiServiceStreamingPublisherTest {

    interface StringStreamer {
        Flow.Publisher<String> chat(String message);
    }

    interface EventStreamer {
        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    interface BoxStringStreamer {
        ReactiveBox<String> chat(String message);
    }

    interface BoxEventStreamer {
        ReactiveBox<AiServiceStreamingEvent> chat(String message);
    }

    static class Tools {

        @Tool
        String currentTemperature(String city) {
            return "42";
        }
    }

    static class MultipleTools {

        final List<String> invoked = new CopyOnWriteArrayList<>();

        @Tool
        String currentTemperature(String city) {
            invoked.add("currentTemperature");
            return "42";
        }

        @Tool
        String currentHumidity(String city) {
            invoked.add("currentHumidity");
            return "69";
        }
    }

    static class AsyncTools {

        @Tool
        CompletableFuture<String> currentTemperature(String city) {
            return CompletableFuture.supplyAsync(() -> "42", CompletableFuture.delayedExecutor(20, TimeUnit.MILLISECONDS));
        }
    }

    static class MixedTools {

        @Tool
        String currentTemperature(String city) {
            return "42";
        }

        @Tool
        CompletableFuture<String> currentHumidity(String city) {
            return CompletableFuture.supplyAsync(() -> "69", CompletableFuture.delayedExecutor(20, TimeUnit.MILLISECONDS));
        }
    }

    static class ThrowingTools {

        @Tool
        String currentTemperature(String city) {
            throw new RuntimeException("boom");
        }
    }

    static class CompensatingTools {

        final List<String> calls = new CopyOnWriteArrayList<>();

        @Tool
        String credit(String city) {
            calls.add("credit");
            return "credited";
        }

        @CompensateFor("credit")
        void uncredit(String city) {
            calls.add("uncredit");
        }

        @Tool
        String withdraw(String city) {
            calls.add("withdraw");
            throw new RuntimeException("insufficient funds");
        }
    }

    static class WeatherTool {

        @Tool
        String getWeather(String city) {
            return "sunny";
        }
    }

    static class GatedTool {

        private final CountDownLatch started;

        GatedTool(CountDownLatch started) {
            this.started = started;
        }

        @Tool
        String currentTemperature(String city) {
            started.countDown();
            return "42";
        }
    }

    private static ToolSpecification temperatureSpecification() {
        return ToolSpecification.builder()
                .name("currentTemperature")
                .parameters(JsonObjectSchema.builder().addStringProperty("city").build())
                .build();
    }

    private static ToolExecutionRequest temperatureRequest() {
        return toolRequest("1", "currentTemperature");
    }

    private static ToolExecutionRequest humidityRequest() {
        return toolRequest("2", "currentHumidity");
    }

    private static ToolExecutionRequest toolRequest(String id, String name) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments("{\"city\": \"Berlin\"}")
                .build();
    }

    @Test
    void streams_text_tokens_without_tools() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello"));

        StringStreamer assistant = AiServices.builder(StringStreamer.class)
                .streamingChatModel(model)
                .build();

        Collected<String> collected = collect(assistant.chat("Hi"));

        assertThat(collected.error).isNull();
        assertThat(String.join("", collected.items)).isEqualTo("Hello");
    }

    @Test
    void streams_events_without_tools() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello"));

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("Hi"));

        assertThat(collected.error).isNull();

        String partialText = collected.items.stream()
                .filter(e -> e instanceof PartialResponseEvent)
                .map(e -> ((PartialResponseEvent) e).partialResponse().text())
                .reduce("", String::concat);
        assertThat(partialText).isEqualTo("Hello");

        List<FinalResponseEvent> finalResponses = collected.items.stream()
                .filter(e -> e instanceof FinalResponseEvent)
                .map(e -> (FinalResponseEvent) e)
                .toList();
        assertThat(finalResponses).hasSize(1);
        assertThat(finalResponses.get(0).chatResponse().aiMessage().text()).isEqualTo("Hello");

        assertThat(collected.items).noneMatch(e -> e instanceof IntermediateResponseEvent);
        // the FinalResponseEvent is emitted last
        assertThat(collected.items.get(collected.items.size() - 1)).isInstanceOf(FinalResponseEvent.class);
    }

    @Test
    void streams_events_across_tool_rounds_in_order() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(temperatureRequest()), AiMessage.from("It is 42 degrees"));

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(new Tools())
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the temperature in Berlin?"));

        assertThat(collected.error).isNull();

        // exactly one intermediate (tool-calling) response, and it carries the tool request
        List<IntermediateResponseEvent> intermediates = collected.items.stream()
                .filter(e -> e instanceof IntermediateResponseEvent)
                .map(e -> (IntermediateResponseEvent) e)
                .toList();
        assertThat(intermediates).hasSize(1);
        assertThat(intermediates.get(0).chatResponse().aiMessage().hasToolExecutionRequests())
                .isTrue();

        // before/after tool execution events
        List<BeforeToolExecutionEvent> before = collected.items.stream()
                .filter(e -> e instanceof BeforeToolExecutionEvent)
                .map(e -> (BeforeToolExecutionEvent) e)
                .toList();
        assertThat(before).hasSize(1);
        assertThat(before.get(0).beforeToolExecution().request().name()).isEqualTo("currentTemperature");

        List<AfterToolExecutionEvent> executed = collected.items.stream()
                .filter(e -> e instanceof AfterToolExecutionEvent)
                .map(e -> (AfterToolExecutionEvent) e)
                .toList();
        assertThat(executed).hasSize(1);
        assertThat(executed.get(0).toolExecution().result()).isEqualTo("42");

        // the final answer streams as partial responses of the second round
        String partialText = collected.items.stream()
                .filter(e -> e instanceof PartialResponseEvent)
                .map(e -> ((PartialResponseEvent) e).partialResponse().text())
                .reduce("", String::concat);
        assertThat(partialText).isEqualTo("It is 42 degrees");

        // ordering: BeforeToolExecution -> AfterToolExecution -> partials of the final answer -> final.
        // Tools start eagerly on their CompleteToolCall, so a tool's BeforeToolExecutionEvent may be emitted
        // before the round's IntermediateResponseEvent; that relative order is intentionally not asserted.
        int beforeIndex = indexOfType(collected.items, BeforeToolExecutionEvent.class);
        int executedIndex = indexOfType(collected.items, AfterToolExecutionEvent.class);
        int firstPartialOfFinalAnswer = firstPartialAfter(collected.items, executedIndex);
        assertThat(beforeIndex).isLessThan(executedIndex);
        assertThat(executedIndex).isLessThan(firstPartialOfFinalAnswer);

        // exactly one FinalResponseEvent (the final answer), emitted last
        List<FinalResponseEvent> terminalResponses = collected.items.stream()
                .filter(e -> e instanceof FinalResponseEvent)
                .map(e -> (FinalResponseEvent) e)
                .toList();
        assertThat(terminalResponses).hasSize(1);
        assertThat(terminalResponses.get(0).chatResponse().aiMessage().text()).isEqualTo("It is 42 degrees");
        assertThat(collected.items.get(collected.items.size() - 1)).isInstanceOf(FinalResponseEvent.class);

        // the model received the tool result in the second request
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages())
                .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                .singleElement()
                .satisfies(message ->
                        assertThat(((ToolExecutionResultMessage) message).text()).isEqualTo("42"));
    }

    @Test
    void propagates_raw_provider_events() throws Exception {
        RawStreamingEvent raw = RawStreamingEvent.of("{\"q\":\"berlin\"}");
        StreamingEventChatModelMock model =
                StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello")).withRawEvent(raw);

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("Hi"));

        assertThat(collected.error).isNull();
        List<RawEvent> rawEvents = collected.items.stream()
                .filter(e -> e instanceof RawEvent)
                .map(e -> (RawEvent) e)
                .toList();
        assertThat(rawEvents).hasSize(1);
        assertThat(rawEvents.get(0).rawStreamingEvent()).isSameAs(raw);
        assertThat(rawEvents.get(0).invocationContext()).isNotNull();
    }

    @Test
    void streams_only_final_answer_text_across_tool_rounds() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(temperatureRequest()), AiMessage.from("It is 42 degrees"));

        StringStreamer assistant = AiServices.builder(StringStreamer.class)
                .streamingChatModel(model)
                .tools(new Tools())
                .build();

        Collected<String> collected = collect(assistant.chat("What is the temperature in Berlin?"));

        assertThat(collected.error).isNull();
        assertThat(String.join("", collected.items)).isEqualTo("It is 42 degrees");
    }

    @Test
    void executes_async_tool() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(temperatureRequest()), AiMessage.from("It is 42 degrees"));

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(new AsyncTools())
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the temperature in Berlin?"));

        assertThat(collected.error).isNull();
        assertThat(afterToolExecutions(collected))
                .singleElement()
                .satisfies(e -> assertThat(e.toolExecution().result()).isEqualTo("42"));
        assertThat(finalText(collected)).isEqualTo("It is 42 degrees");
        assertToolResultsSentToLlm(model, 1, "42");
    }

    @Test
    void executes_multiple_tools_in_one_round_sequentially() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(temperatureRequest(), humidityRequest()),
                AiMessage.from("42 degrees, 69 percent"));
        MultipleTools tools = new MultipleTools();

        // A single-threaded executor runs the tools one at a time, in request order (still off the delivery
        // thread) — the recommended way to get sequential tool execution.
        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(tools)
                .executeToolsConcurrently(Executors.newSingleThreadExecutor())
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the weather in Berlin?"));

        assertThat(collected.error).isNull();
        // single-threaded executor -> tools run one after another, in request order
        assertThat(tools.invoked).containsExactly("currentTemperature", "currentHumidity");
        assertThat(afterToolExecutions(collected)).hasSize(2);
        assertThat(finalText(collected)).isEqualTo("42 degrees, 69 percent");
        assertToolResultsSentToLlm(model, 1, "42", "69");
    }

    @Test
    void starts_tools_eagerly_on_complete_tool_call_by_default() throws Exception {
        // The Publisher path executes tools concurrently by default, starting each on its CompleteToolCall.
        // The model waits for the tool to start before completing the tool-calling round, so this only
        // completes if the tool is started eagerly (on CompleteToolCall) rather than after the model response
        // finishes; otherwise it would deadlock. No executeToolsConcurrently() call -> proves the default.
        CountDownLatch toolStarted = new CountDownLatch(1);
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                        AiMessage.from(temperatureRequest()), AiMessage.from("It is 42 degrees"))
                .withToolRoundCompletionGate(toolStarted);

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(new GatedTool(toolStarted))
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the temperature in Berlin?"));

        assertThat(collected.error).isNull();
        assertThat(finalText(collected)).isEqualTo("It is 42 degrees");
        assertToolResultsSentToLlm(model, 1, "42");
    }

    @Test
    void executes_multiple_tools_concurrently() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(temperatureRequest(), humidityRequest()),
                AiMessage.from("42 degrees, 69 percent"));
        MultipleTools tools = new MultipleTools();

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(tools)
                .executeToolsConcurrently()
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the weather in Berlin?"));

        assertThat(collected.error).isNull();
        assertThat(tools.invoked).containsExactlyInAnyOrder("currentTemperature", "currentHumidity");
        assertThat(afterToolExecutions(collected)).hasSize(2);
        assertThat(finalText(collected)).isEqualTo("42 degrees, 69 percent");
        assertToolResultsSentToLlm(model, 1, "42", "69");
    }

    @Test
    void executes_mixed_sync_and_async_tools_in_one_round() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(temperatureRequest(), humidityRequest()),
                AiMessage.from("42 degrees, 69 percent"));

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(new MixedTools())
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the weather in Berlin?"));

        assertThat(collected.error).isNull();
        assertThat(afterToolExecutions(collected)).hasSize(2);
        assertThat(finalText(collected)).isEqualTo("42 degrees, 69 percent");
        assertToolResultsSentToLlm(model, 1, "42", "69");
    }

    @Test
    void executes_two_sequential_tool_rounds() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(temperatureRequest()),
                AiMessage.from(humidityRequest()),
                AiMessage.from("42 degrees, 69 percent"));

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(new MultipleTools())
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the weather in Berlin?"));

        assertThat(collected.error).isNull();
        assertThat(intermediateResponses(collected)).hasSize(2);
        assertThat(afterToolExecutions(collected)).hasSize(2);
        assertThat(model.requests()).hasSize(3);
        assertThat(finalText(collected)).isEqualTo("42 degrees, 69 percent");
        assertThat(collected.items.get(collected.items.size() - 1)).isInstanceOf(FinalResponseEvent.class);
    }

    @Test
    void fails_stream_when_tool_throws_by_default() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(temperatureRequest()), AiMessage.from("unreachable"));

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(new ThrowingTools())
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the temperature in Berlin?"));

        // by default (new async modes), a tool execution failure fails the stream; it is NOT sent to the LLM
        assertThat(collected.error).isNotNull().hasMessageContaining("boom");
        assertThat(model.requests()).hasSize(1); // failed before a second model round

        // even though the tool threw, its execution is still reported: every BeforeToolExecutionEvent is balanced
        // by an AfterToolExecutionEvent, flagged as failed, so an observer never sees a dangling "before".
        assertThat(collected.items).filteredOn(e -> e instanceof BeforeToolExecutionEvent).hasSize(1);
        assertThat(afterToolExecutions(collected)).singleElement().satisfies(e -> {
            assertThat(e.toolExecution().request().name()).isEqualTo("currentTemperature");
            assertThat(e.toolExecution().hasFailed()).isTrue();
        });
        assertThat(collected.items).noneMatch(e -> e instanceof FinalResponseEvent);
    }

    @Test
    void works_with_chat_memory_across_a_tool_round() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(temperatureRequest()), AiMessage.from("It is 42 degrees"));

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(new Tools())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the temperature in Berlin?"));

        assertThat(collected.error).isNull();
        assertThat(finalText(collected)).isEqualTo("It is 42 degrees");
        assertToolResultsSentToLlm(model, 1, "42");
    }

    @Test
    void fails_when_max_tool_calling_round_trips_exceeded() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(temperatureRequest()), AiMessage.from(temperatureRequest()));

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(new Tools())
                .maxToolCallingRoundTrips(1)
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the temperature in Berlin?"));

        assertThat(collected.error).isNotNull();
        assertThat(collected.error).hasMessageContaining("round trips");
    }

    @Test
    void uses_tool_provider_with_async_capable_executor() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(temperatureRequest()), AiMessage.from("It is 42 degrees"));

        ToolExecutor asyncExecutor = new ToolExecutor() {
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return "42";
            }

            @Override
            public CompletableFuture<ToolExecutionResult> executeAsync(
                    ToolExecutionRequest request, InvocationContext context) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.builder().resultText("42").build());
            }
        };
        ToolProvider toolProvider =
                request -> ToolProviderResult.builder().add(temperatureSpecification(), asyncExecutor).build();

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .toolProvider(toolProvider)
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the temperature in Berlin?"));

        assertThat(collected.error).isNull();
        assertThat(afterToolExecutions(collected))
                .singleElement()
                .satisfies(e -> assertThat(e.toolExecution().result()).isEqualTo("42"));
        assertThat(finalText(collected)).isEqualTo("It is 42 degrees");
        assertToolResultsSentToLlm(model, 1, "42");
    }

    @Test
    void fails_when_tool_executor_does_not_support_async_execution() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(temperatureRequest()), AiMessage.from("It is 42 degrees"));

        // a sync-only ToolExecutor (does not override executeAsync) is incompatible with the reactive path
        ToolExecutor syncOnlyExecutor = (request, memoryId) -> "42";
        ToolProvider toolProvider =
                request -> ToolProviderResult.builder().add(temperatureSpecification(), syncOnlyExecutor).build();

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .toolProvider(toolProvider)
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the temperature in Berlin?"));

        // the gap is surfaced as a failure of the invocation, not routed to the tool error handler / the LLM
        assertThat(collected.error)
                .isExactlyInstanceOf(AsyncNotSupportedException.class)
                .hasMessageContaining("does not support asynchronous execution");
    }

    @Test
    void recovers_from_tool_argument_parse_error_via_custom_handler() throws Exception {
        ToolExecutionRequest invalidArguments = ToolExecutionRequest.builder()
                .id("1")
                .name("getWeather")
                .arguments("{ invalid json }")
                .build();
        StreamingEventChatModelMock model =
                StreamingEventChatModelMock.thatStreams(AiMessage.from(invalidArguments), AiMessage.from("sunny"));

        ToolArgumentsErrorHandler errorHandler = (error, context) -> ToolErrorHandlerResult.text("Invalid JSON, try again");

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(new WeatherTool())
                .toolArgumentsErrorHandler(errorHandler)
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the weather in Berlin?"));

        assertThat(collected.error).isNull();
        assertThat(afterToolExecutions(collected)).singleElement().satisfies(e -> {
            assertThat(e.toolExecution().hasFailed()).isTrue();
            assertThat(e.toolExecution().result()).isEqualTo("Invalid JSON, try again");
        });
        // the customized error text is sent to the LLM so it can retry
        assertToolResultsSentToLlm(model, 1, "Invalid JSON, try again");
        assertThat(finalText(collected)).isEqualTo("sunny");
    }

    @Test
    void sends_tool_argument_parse_error_to_llm_by_default() throws Exception {
        ToolExecutionRequest invalidArguments = ToolExecutionRequest.builder()
                .id("1")
                .name("getWeather")
                .arguments("{ invalid json }")
                .build();
        StreamingEventChatModelMock model =
                StreamingEventChatModelMock.thatStreams(AiMessage.from(invalidArguments), AiMessage.from("sunny"));

        // by default (new async modes), an argument-parse error is sent to the LLM so it can retry
        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(new WeatherTool())
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the weather in Berlin?"));

        assertThat(collected.error).isNull();
        assertThat(afterToolExecutions(collected))
                .singleElement()
                .satisfies(e -> assertThat(e.toolExecution().hasFailed()).isTrue());
        assertThat(finalText(collected)).isEqualTo("sunny");
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void fails_stream_when_tool_execution_error_handler_rethrows() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(temperatureRequest()), AiMessage.from("unreachable"));

        // a handler that rethrows turns a tool failure into a fatal invocation failure (not sent to the LLM)
        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(new ThrowingTools())
                .toolExecutionErrorHandler((error, context) -> {
                    throw new RuntimeException("fatal: " + error.getMessage());
                })
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the temperature in Berlin?"));

        assertThat(collected.error).isInstanceOf(RuntimeException.class).hasMessageContaining("fatal: boom");
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void compensated_tool_emits_a_tool_compensated_event_in_the_stream() throws Exception {
        CompensatingTools tools = new CompensatingTools();
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.builder()
                        .toolExecutionRequests(List.of(toolRequest("1", "credit"), toolRequest("2", "withdraw")))
                        .build(),
                AiMessage.from("unreachable"));

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(tools)
                .compensateOnToolErrors(true)
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("transfer"));

        // the failing withdraw fails the stream (default rethrow handler), but the successful credit is rolled back
        assertThat(collected.error).isNotNull().hasMessageContaining("insufficient funds");
        assertThat(tools.calls).contains("credit", "withdraw", "uncredit");
        // and the rollback is observable inline as a ToolCompensatedEvent
        List<AiServiceStreamingEvent.ToolCompensatedEvent> compensated = collected.items.stream()
                .filter(e -> e instanceof AiServiceStreamingEvent.ToolCompensatedEvent)
                .map(e -> (AiServiceStreamingEvent.ToolCompensatedEvent) e)
                .toList();
        assertThat(compensated).singleElement().satisfies(e -> {
            assertThat(e.toolExecution().request().name()).isEqualTo("credit");
            assertThat(e.reason()).isEqualTo(CompensationReason.TOOL_EXECUTION_FAILED);
        });
    }

    @Test
    void fails_stream_when_hallucinated_tool_is_requested() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(toolRequest("1", "nonExistentTool")), AiMessage.from("unreachable"));

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(new Tools()) // only currentTemperature exists; the model "hallucinates" nonExistentTool
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("What is the temperature in Berlin?"));

        assertThat(collected.error).isNotNull();
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void emits_retrieved_contents_event() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello"));

        // A genuinely async content retriever (overrides retrieveAsync): the reactive RAG path stays non-blocking
        ContentRetriever asyncRetriever = new ContentRetriever() {
            @Override
            public List<Content> retrieve(Query query) {
                return List.of(Content.from("relevant document"));
            }

            @Override
            public CompletableFuture<List<Content>> retrieveAsync(Query query) {
                return CompletableFuture.completedFuture(retrieve(query));
            }
        };

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .contentRetriever(asyncRetriever)
                .build();

        Collected<AiServiceStreamingEvent> collected = collect(assistant.chat("Hi"));

        assertThat(collected.error).isNull();
        // mirrors TokenStream.onRetrieved: retrieved content is surfaced once, before the response chunks
        assertThat(collected.items)
                .filteredOn(e -> e instanceof RetrievedContentsEvent)
                .singleElement()
                .satisfies(e -> assertThat(((RetrievedContentsEvent) e).contents())
                        .extracting(content -> content.textSegment().text())
                        .contains("relevant document"));
        int retrievedIndex = indexOfType(collected.items, RetrievedContentsEvent.class);
        int firstPartialIndex = indexOfType(collected.items, PartialResponseEvent.class);
        assertThat(retrievedIndex).isGreaterThanOrEqualTo(0).isLessThan(firstPartialIndex);
    }

    @Test
    void adapts_string_stream_via_publisher_adapter() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello"));

        BoxStringStreamer assistant = AiServices.builder(BoxStringStreamer.class)
                .streamingChatModel(model)
                .build();

        ReactiveBox<String> box = assistant.chat("Hi");
        assertThat(box).isNotNull();

        Collected<String> collected = collect(box.publisher());
        assertThat(collected.error).isNull();
        assertThat(String.join("", collected.items)).isEqualTo("Hello");
    }

    @Test
    void adapts_event_stream_via_publisher_adapter() throws Exception {
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello"));

        BoxEventStreamer assistant = AiServices.builder(BoxEventStreamer.class)
                .streamingChatModel(model)
                .build();

        ReactiveBox<AiServiceStreamingEvent> box = assistant.chat("Hi");
        assertThat(box).isNotNull();

        Collected<AiServiceStreamingEvent> collected = collect(box.publisher());
        assertThat(collected.error).isNull();
        assertThat(collected.items.get(collected.items.size() - 1)).isInstanceOf(FinalResponseEvent.class);
    }

    @Test
    void runs_already_started_tool_to_completion_and_emits_nothing_after_cancellation() throws Exception {

        CountDownLatch toolStarted = new CountDownLatch(1);
        CountDownLatch releaseTool = new CountDownLatch(1);
        AtomicBoolean toolFinished = new AtomicBoolean(false);

        class SlowTool {

            @Tool
            String slowTool(String city) {
                toolStarted.countDown();
                try {
                    releaseTool.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                toolFinished.set(true);
                return "42";
            }
        }

        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(toolRequest("1", "slowTool")), AiMessage.from("unreachable"));

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(new SlowTool())
                .build();

        List<AiServiceStreamingEvent> items = new CopyOnWriteArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

        assistant.chat("go").subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                subscription.set(s);
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AiServiceStreamingEvent event) {
                items.add(event);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });

        assertThat(toolStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("the tool is running").isTrue();
        subscription.get().cancel();
        releaseTool.countDown(); // let the already-running tool finish

        Thread.sleep(200);
        // the tool is NOT interrupted; and after cancellation nothing more is emitted and no further round runs
        assertThat(toolFinished).as("an already-started tool is not interrupted by cancellation").isTrue();
        assertThat(model.requests()).hasSize(1);
        assertThat(items).noneMatch(e -> e instanceof FinalResponseEvent);
        assertThat(completed.get()).isFalse();
        assertThat(error.get()).isNull();
    }

    @Test
    void cancelling_the_subscription_rolls_back_compensable_tools_and_fires_a_compensated_event() throws Exception {

        CountDownLatch toolStarted = new CountDownLatch(1);
        CountDownLatch releaseTool = new CountDownLatch(1);
        List<String> calls = new CopyOnWriteArrayList<>();

        class SlowCompensatingTool {

            @Tool
            String credit(String city) {
                calls.add("credit");
                toolStarted.countDown();
                try {
                    releaseTool.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "credited";
            }

            @CompensateFor("credit")
            void uncredit(String city) {
                calls.add("uncredit");
            }
        }

        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(toolRequest("1", "credit")), AiMessage.from("unreachable"));

        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .tools(new SlowCompensatingTool())
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add)
                .build();

        AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        assistant.chat("transfer").subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                subscription.set(s);
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AiServiceStreamingEvent event) {}

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        assertThat(toolStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("the compensable tool is running").isTrue();
        subscription.get().cancel();
        releaseTool.countDown(); // let the already-running tool finish (drain), then it is rolled back

        long deadline = System.currentTimeMillis() + 5_000;
        while (compensatedEvents.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        // drain-then-rollback: the successful credit is compensated even though the subscription was cancelled
        assertThat(calls).contains("credit", "uncredit");
        assertThat(compensatedEvents).singleElement().satisfies(event -> {
            assertThat(event.request().name()).isEqualTo("credit");
            assertThat(event.reason()).isEqualTo(CompensationReason.INVOCATION_CANCELLED);
        });
    }

    @Test
    void concurrent_cancellation_and_round_completion_never_double_compensate() throws Exception {
        // Regression guard for the accumulator data race: a subscriber cancel and a round's completion touch the
        // shared compensable-tools accumulator on different threads. Aligning the tool's return with the cancel via
        // a barrier maximises the overlap; the compensating action must never run twice for one tool (no double
        // refund) and the accumulator access must not throw.
        for (int i = 0; i < 60; i++) {
            AtomicInteger creditCount = new AtomicInteger();
            AtomicInteger uncreditCount = new AtomicInteger();
            CyclicBarrier barrier = new CyclicBarrier(2);

            class RacyTools {

                @Tool
                String credit(String city) {
                    creditCount.incrementAndGet();
                    return "credited";
                }

                @CompensateFor("credit")
                void uncredit(String city) {
                    uncreditCount.incrementAndGet();
                }

                // fails, so the round-completion path also compensates 'credit' - racing the cancel path on the
                // same tool. Blocks on the barrier so its failure lands together with the cancel.
                @Tool
                String withdraw(String city) {
                    try {
                        barrier.await(1, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        // barrier broken/timeout - fine for the race window
                    }
                    throw new RuntimeException("insufficient funds");
                }
            }

            StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                    AiMessage.builder()
                            .toolExecutionRequests(List.of(toolRequest("1", "credit"), toolRequest("2", "withdraw")))
                            .build(),
                    AiMessage.from("done"));
            EventStreamer assistant = AiServices.builder(EventStreamer.class)
                    .streamingChatModel(model)
                    .tools(new RacyTools())
                    .compensateOnToolErrors(true)
                    .build();

            AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
            assistant.chat("go").subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription s) {
                    subscription.set(s);
                    s.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(AiServiceStreamingEvent event) {}

                @Override
                public void onError(Throwable throwable) {}

                @Override
                public void onComplete() {}
            });

            // cancel exactly as the tool returns, so whenCancelled races the round-completion callback
            Thread canceller = new Thread(() -> {
                try {
                    barrier.await(1, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return;
                }
                subscription.get().cancel();
            });
            canceller.start();
            canceller.join(2000);

            // let any rollback settle
            Thread.sleep(25);
            assertThat(uncreditCount.get())
                    .as("iteration %d: a compensable tool must be rolled back at most once (no double refund)", i)
                    .isLessThanOrEqualTo(1);
        }
    }

    @Test
    void cancelling_the_subscription_stops_the_model_stream() throws Exception {
        StreamingEventChatModelMock model =
                StreamingEventChatModelMock.thatStreamsSlowly(50, AiMessage.from("0123456789"));

        StringStreamer assistant = AiServices.builder(StringStreamer.class)
                .streamingChatModel(model)
                .build();

        List<String> items = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Boolean> completed = new AtomicReference<>(false);
        CountDownLatch firstItem = new CountDownLatch(1);

        assistant.chat("Hi").subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                items.add(item);
                subscription.cancel();
                firstItem.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });

        assertThat(firstItem.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // the cancellation should propagate to the model's reactive stream, which then stops producing
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!model.cancellationObserved() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(model.cancellationObserved()).as("model observed cancellation").isTrue();

        assertThat(completed.get()).as("stream did not complete after cancellation").isFalse();
        assertThat(error.get()).isNull();
        assertThat(items.size()).isLessThan(10);
    }

    private static String finalText(Collected<AiServiceStreamingEvent> collected) {
        return collected.items.stream()
                .filter(e -> e instanceof FinalResponseEvent)
                .map(e -> ((FinalResponseEvent) e).chatResponse().aiMessage().text())
                .findFirst()
                .orElse(null);
    }

    private static List<AfterToolExecutionEvent> afterToolExecutions(Collected<AiServiceStreamingEvent> collected) {
        return collected.items.stream()
                .filter(e -> e instanceof AfterToolExecutionEvent)
                .map(e -> (AfterToolExecutionEvent) e)
                .toList();
    }

    private static List<IntermediateResponseEvent> intermediateResponses(Collected<AiServiceStreamingEvent> collected) {
        return collected.items.stream()
                .filter(e -> e instanceof IntermediateResponseEvent)
                .map(e -> (IntermediateResponseEvent) e)
                .toList();
    }

    private static void assertToolResultsSentToLlm(
            StreamingEventChatModelMock model, int requestIndex, String... expectedResultTexts) {
        assertThat(model.requests().get(requestIndex).messages())
                .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                .extracting(message -> ((ToolExecutionResultMessage) message).text())
                .containsExactlyInAnyOrder(expectedResultTexts);
    }

    private static int indexOfType(List<?> items, Class<?> type) {
        for (int i = 0; i < items.size(); i++) {
            if (type.isInstance(items.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int firstPartialAfter(List<?> items, int afterIndex) {
        for (int i = afterIndex + 1; i < items.size(); i++) {
            if (items.get(i) instanceof PartialResponseEvent) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void streaming_buffer_size_must_be_positive() {
        // Validation happens eagerly at build() time (not in the streamingBufferSize(...) setter).
        assertThatThrownBy(() -> AiServices.builder(EventStreamer.class)
                        .streamingChatModel(StreamingEventChatModelMock.thatStreams(AiMessage.from("Hi")))
                        .streamingBufferSize(0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void small_buffer_overflows_when_the_subscriber_does_not_keep_up() throws Exception {
        // A long response (one PartialResponseEvent per character) + a tiny buffer + a subscriber that requests
        // only once: the bounded back-pressure buffer overflows and the stream fails fast (rather than dropping
        // events or buffering unbounded).
        StreamingEventChatModelMock model =
                StreamingEventChatModelMock.thatStreams(AiMessage.from("This response is far longer than the buffer."));

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .streamingBufferSize(4)
                .build();

        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        assistant.chat("Hi").subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(1); // request once and never again, so the buffer fills up
            }

            @Override
            public void onNext(AiServiceStreamingEvent event) {}

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

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isInstanceOf(IllegalStateException.class);
    }

    interface SystemMessagedStringStreamer {
        @SystemMessage("You are a helpful assistant.")
        Flow.Publisher<String> chat(String message);
    }

    static class SyncOnlyChatMemoryStore implements ChatMemoryStore {

        private List<ChatMessage> messages = List.of();

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return messages;
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            this.messages = messages;
        }

        @Override
        public void deleteMessages(Object memoryId) {
            this.messages = List.of();
        }
    }

    @Test
    void reactive_stream_signals_onError_when_store_does_not_support_async() throws Exception {
        // With a system message, the (deferred, on-subscribe) message assembly calls the store's async methods
        // synchronously; a store that does not implement them fails synchronously. subscribe() must not throw or
        // hang - the failure must reach the subscriber as onError after onSubscribe, translated to the user-facing
        // UnsupportedFeatureException (consistent with the rest of the async path).
        SystemMessagedStringStreamer assistant = AiServices.builder(SystemMessagedStringStreamer.class)
                .streamingChatModel(StreamingEventChatModelMock.thatStreams(AiMessage.from("Hi")))
                .chatMemory(MessageWindowChatMemory.builder()
                        .maxMessages(10)
                        .chatMemoryStore(new SyncOnlyChatMemoryStore())
                        .build())
                .build();

        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        assistant.chat("Hi").subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {}

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

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isExactlyInstanceOf(UnsupportedFeatureException.class);
    }

    private static <T> Collected<T> collect(Flow.Publisher<T> publisher) throws InterruptedException {
        List<T> items = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T item) {
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

        assertThat(latch.await(10, TimeUnit.SECONDS)).as("stream completed within 10s").isTrue();
        return new Collected<>(items, error.get());
    }

    private record Collected<T>(List<T> items, Throwable error) {}
}
