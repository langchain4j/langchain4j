package dev.langchain4j.service;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ToolErrorVisibleToLlm;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.ToolCompensatedEvent;
import dev.langchain4j.observability.api.listener.AiServiceCompletedListener;
import dev.langchain4j.observability.api.listener.ToolCompensatedEventListener;
import dev.langchain4j.service.AiServiceStreamingEvent.FinalResponseEvent;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Providers reject a request whose history has a tool call without a result, so however an AI Service invocation
 * ends, in every mode, the chat memory must not be left with an unanswered tool call.
 */
class AiServiceToolCallMemoryTest {

    private static final ToolExecutionRequest BOOK_HOTEL = ToolExecutionRequest.builder()
            .id("call-1")
            .name("book")
            .arguments("{\"arg0\": \"hotel\"}")
            .build();

    private static final ToolExecutionRequest GET_TEMPERATURE = ToolExecutionRequest.builder()
            .id("call-2")
            .name("temperature")
            .arguments("{}")
            .build();

    static class Booking {

        @Tool(returnBehavior = IMMEDIATE)
        String book(String what) {
            return "Booking#42";
        }
    }

    static class CompensableBooking {

        final AtomicInteger cancellations = new AtomicInteger();

        @Tool(returnBehavior = IMMEDIATE)
        String book(String what) {
            return "Booking#42";
        }

        @CompensateFor("book")
        void cancelBooking(String what) {
            cancellations.incrementAndGet();
        }
    }

    static class Weather {

        @Tool
        String temperature() {
            return "42";
        }
    }

    private static final ToolExecutionRequest DIVIDE_FOUR_BY_TWO = ToolExecutionRequest.builder()
            .id("call-3")
            .name("divide")
            .arguments("{\"arg0\": 4, \"arg1\": 2}")
            .build();

    private static final ToolExecutionRequest DIVIDE_BY_ZERO = ToolExecutionRequest.builder()
            .id("call-4")
            .name("divide")
            .arguments("{\"arg0\": 1, \"arg1\": 0}")
            .build();

    private static final ToolExecutionRequest DIVIDE_WITH_INVALID_ARGUMENTS = ToolExecutionRequest.builder()
            .id("call-5")
            .name("divide")
            .arguments("{\"arg0\": \"one\", \"arg1\": 2}")
            .build();

    private static final ToolExecutionRequest NON_EXISTENT_TOOL = ToolExecutionRequest.builder()
            .id("call-6")
            .name("multiply")
            .arguments("{}")
            .build();

    private static final String DIVIDE_FAILED = "Tool 'divide' failed, so the request was stopped";

    static class Calculator {

        @Tool
        double divide(double a, double b) {
            if (b == 0) {
                throw new IllegalArgumentException("Division by zero in /opt/app/Calculator.java");
            }
            return a / b;
        }
    }

    private static final ToolExecutionRequest FIND_ORDER = ToolExecutionRequest.builder()
            .id("call-8")
            .name("findOrder")
            .arguments("{\"arg0\": \"42\"}")
            .build();

    static class Orders {

        @Tool
        String findOrder(String id) {
            throw ToolErrorVisibleToLlm.from("There is no order " + id);
        }
    }

    public static class Report {

        public String getTitle() {
            throw new IllegalStateException("the title cannot be read");
        }
    }

    static class Reports {

        @Tool
        Report report() {
            return new Report();
        }
    }

    private static final ToolExecutionRequest REPORT = ToolExecutionRequest.builder()
            .id("call-7")
            .name("report")
            .arguments("{}")
            .build();

    // a memory whose blocking add fails for tool results, like a store that is down
    static class ThrowingBlockingToolResultMemory extends SlowToolResultMemory {

        @Override
        public void add(ChatMessage message) {
            if (message instanceof ToolExecutionResultMessage) {
                throw new IllegalStateException("store is down");
            }
            super.add(message);
        }
    }

    interface SyncAssistant {

        Result<String> chat(String message);
    }

    interface TokenStreamAssistant {

        TokenStream chat(String message);
    }

    interface AsyncAssistant {

        CompletableFuture<Result<String>> chat(String message);
    }

    interface ReactiveAssistant {

        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    // delegates to a real memory, but makes the tool result visible only once the test releases it, like a slow store
    static class SlowToolResultMemory implements ChatMemory {

        private final ChatMemory delegate = MessageWindowChatMemory.withMaxMessages(10);
        final CompletableFuture<Void> toolResultPersisted = new CompletableFuture<>();
        final CountDownLatch toolResultPersistRequested = new CountDownLatch(1);

        @Override
        public Object id() {
            return delegate.id();
        }

        @Override
        public void add(ChatMessage message) {
            delegate.add(message);
        }

        @Override
        public List<ChatMessage> messages() {
            return delegate.messages();
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public CompletableFuture<Void> addAsync(List<ChatMessage> messages) {
            if (messages.stream().noneMatch(ToolExecutionResultMessage.class::isInstance)) {
                messages.forEach(delegate::add);
                return CompletableFuture.completedFuture(null);
            }
            toolResultPersistRequested.countDown();
            return toolResultPersisted.thenRun(() -> messages.forEach(delegate::add));
        }

        @Override
        public CompletableFuture<Void> setAsync(List<ChatMessage> messages) {
            delegate.clear();
            messages.forEach(delegate::add);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<ChatMessage>> messagesAsync() {
            return CompletableFuture.completedFuture(delegate.messages());
        }
    }

    static class ThrowingToolResultMemory extends SlowToolResultMemory {

        @Override
        public CompletableFuture<Void> addAsync(List<ChatMessage> messages) {
            if (messages.stream().anyMatch(ToolExecutionResultMessage.class::isInstance)) {
                throw new IllegalStateException("store is down");
            }
            return super.addAsync(messages);
        }
    }

    static class EventCollector implements Flow.Subscriber<AiServiceStreamingEvent> {

        final List<AiServiceStreamingEvent> events = new CopyOnWriteArrayList<>();
        final CompletableFuture<Void> terminated = new CompletableFuture<>();
        volatile Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(AiServiceStreamingEvent event) {
            events.add(event);
        }

        @Override
        public void onError(Throwable throwable) {
            terminated.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            terminated.complete(null);
        }
    }

    private static MockChatModel bookingModel() {
        return MockChatModel.thatResponds(AiMessage.from(BOOK_HOTEL));
    }

    private static MockStreamingChatModel streamingBookingModel() {
        return MockStreamingChatModel.thatResponds(AiMessage.from(BOOK_HOTEL));
    }

    private static void assertToolResultIsInMemory(ChatMemory memory) {
        List<ChatMessage> messages = memory.messages();
        assertThat(messages).hasSize(3);
        assertThat(((AiMessage) messages.get(1)).hasToolExecutionRequests()).isTrue();
        assertThat(messages.get(2)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(((ToolExecutionResultMessage) messages.get(2)).id()).isEqualTo(BOOK_HOTEL.id());
        assertThat(((ToolExecutionResultMessage) messages.get(2)).text()).isEqualTo("Booking#42");
    }

    @Test
    void sync_service_should_store_result_of_immediate_return_tool_in_memory() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        SyncAssistant assistant = AiServices.builder(SyncAssistant.class)
                .chatModel(bookingModel())
                .chatMemory(memory)
                .tools(new Booking())
                .build();

        Result<String> result = assistant.chat("Book a hotel");

        assertThat(result.toolExecutions()).hasSize(1);
        assertToolResultIsInMemory(memory);
    }

    @Test
    void token_stream_service_should_store_result_of_immediate_return_tool_in_memory() throws Exception {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(MockStreamingChatModel.thatResponds(AiMessage.from(BOOK_HOTEL)))
                .chatMemory(memory)
                .tools(new Booking())
                .build();

        CompletableFuture<ChatResponse> completed = new CompletableFuture<>();
        assistant
                .chat("Book a hotel")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start();
        completed.get(10, SECONDS);

        assertToolResultIsInMemory(memory);
    }

    @Test
    void async_service_should_store_result_of_immediate_return_tool_in_memory() throws Exception {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(bookingModel())
                .chatMemory(memory)
                .tools(new Booking())
                .build();

        Result<String> result = assistant.chat("Book a hotel").get(10, SECONDS);

        assertThat(result.toolExecutions()).hasSize(1);
        assertToolResultIsInMemory(memory);
    }

    @Test
    void reactive_service_should_store_result_of_immediate_return_tool_in_memory() throws Exception {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(streamingBookingModel())
                .chatMemory(memory)
                .tools(new Booking())
                .build();

        EventCollector collector = new EventCollector();
        assistant.chat("Book a hotel").subscribe(collector);
        collector.terminated.get(10, SECONDS);

        assertToolResultIsInMemory(memory);
        assertThat(collector.events).last().isInstanceOf(FinalResponseEvent.class);
    }

    @Test
    void async_service_should_send_the_stored_tool_result_to_the_model_on_the_next_turn() throws Exception {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        MockChatModel model =
                MockChatModel.thatResponds(AiMessage.from(BOOK_HOTEL), AiMessage.from("You are welcome"));

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(model)
                .chatMemory(memory)
                .tools(new Booking())
                .build();

        assistant.chat("Book a hotel").get(10, SECONDS);
        assistant.chat("Thanks").get(10, SECONDS);

        List<ChatMessage> nextTurn = model.requests().get(1).messages();
        assertThat(nextTurn).hasSize(4);
        assertThat(nextTurn.get(2)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(nextTurn.get(3)).isEqualTo(UserMessage.from("Thanks"));
    }

    @Test
    void async_service_should_not_complete_before_the_tool_result_is_persisted() throws Exception {

        SlowToolResultMemory memory = new SlowToolResultMemory();

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(bookingModel())
                .chatMemory(memory)
                .tools(new Booking())
                .build();

        CompletableFuture<Result<String>> future = assistant.chat("Book a hotel");

        assertThat(memory.toolResultPersistRequested.await(10, SECONDS)).isTrue();
        assertThat(future).isNotDone();

        memory.toolResultPersisted.complete(null);

        assertThat(future.get(10, SECONDS).toolExecutions()).hasSize(1);
        assertToolResultIsInMemory(memory);
    }

    @Test
    void async_service_should_fail_when_the_tool_result_cannot_be_persisted() throws Exception {

        SlowToolResultMemory memory = new SlowToolResultMemory();

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(bookingModel())
                .chatMemory(memory)
                .tools(new Booking())
                .build();

        CompletableFuture<Result<String>> future = assistant.chat("Book a hotel");

        assertThat(memory.toolResultPersistRequested.await(10, SECONDS)).isTrue();
        memory.toolResultPersisted.completeExceptionally(new IllegalStateException("store is down"));

        assertThat(future)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessage("store is down");
    }

    @Test
    void reactive_service_should_fail_when_the_tool_result_cannot_be_persisted() throws Exception {

        SlowToolResultMemory memory = new SlowToolResultMemory();

        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(streamingBookingModel())
                .chatMemory(memory)
                .tools(new Booking())
                .build();

        EventCollector collector = new EventCollector();
        assistant.chat("Book a hotel").subscribe(collector);

        assertThat(memory.toolResultPersistRequested.await(10, SECONDS)).isTrue();
        memory.toolResultPersisted.completeExceptionally(new IllegalStateException("store is down"));

        assertThat(collector.terminated)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessage("store is down");
    }

    @Test
    void reactive_service_should_fail_when_persisting_the_tool_result_throws() {

        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(streamingBookingModel())
                .chatMemory(new ThrowingToolResultMemory())
                .tools(new Booking())
                .build();

        EventCollector collector = new EventCollector();
        assistant.chat("Book a hotel").subscribe(collector);

        assertThat(collector.terminated)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessage("store is down");
    }

    @Test
    void async_service_should_roll_back_and_not_complete_when_cancelled_while_the_tool_result_is_persisted()
            throws Exception {

        SlowToolResultMemory memory = new SlowToolResultMemory();
        CompensableBooking booking = new CompensableBooking();
        CompletableFuture<ToolCompensatedEvent> compensated = new CompletableFuture<>();
        List<AiServiceCompletedEvent> completedEvents = new CopyOnWriteArrayList<>();

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(bookingModel())
                .chatMemory(memory)
                .tools(booking)
                .compensateOnToolErrors(true)
                .registerListeners(List.of(
                        (ToolCompensatedEventListener) compensated::complete,
                        (AiServiceCompletedListener) completedEvents::add))
                .build();

        CompletableFuture<Result<String>> future = assistant.chat("Book a hotel");

        assertThat(memory.toolResultPersistRequested.await(10, SECONDS)).isTrue();
        future.cancel(true);
        memory.toolResultPersisted.complete(null);

        compensated.get(10, SECONDS);

        assertThat(future).isCancelled();
        assertThat(booking.cancellations).hasValue(1);
        assertThat(completedEvents).isEmpty();
    }

    @Test
    void reactive_service_should_not_complete_when_cancelled_while_the_tool_result_is_persisted() throws Exception {

        SlowToolResultMemory memory = new SlowToolResultMemory();
        List<AiServiceCompletedEvent> completedEvents = new CopyOnWriteArrayList<>();

        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(streamingBookingModel())
                .chatMemory(memory)
                .tools(new Booking())
                .registerListener((AiServiceCompletedListener) completedEvents::add)
                .build();

        EventCollector collector = new EventCollector();
        assistant.chat("Book a hotel").subscribe(collector);

        assertThat(memory.toolResultPersistRequested.await(10, SECONDS)).isTrue();
        collector.subscription.cancel();
        memory.toolResultPersisted.complete(null);

        // give a (wrongly) continued invocation the chance to complete
        Thread.sleep(200);

        assertThat(completedEvents).isEmpty();
    }

    @Test
    void reactive_service_should_not_run_output_guardrails_on_an_immediate_return() throws Exception {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        AtomicInteger guardrailCalls = new AtomicInteger();
        OutputGuardrail guardrail = new OutputGuardrail() {

            @Override
            public OutputGuardrailResult validate(OutputGuardrailRequest request) {
                guardrailCalls.incrementAndGet();
                return success();
            }

            @Override
            public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
                return CompletableFuture.completedFuture(validate(request));
            }
        };

        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(streamingBookingModel())
                .chatMemory(memory)
                .tools(new Booking())
                .outputGuardrails(guardrail)
                .build();

        EventCollector collector = new EventCollector();
        assistant.chat("Book a hotel").subscribe(collector);
        collector.terminated.get(10, SECONDS);

        assertThat(guardrailCalls).hasValue(0);
        assertThat(collector.events).last().isInstanceOf(FinalResponseEvent.class);
        assertToolResultIsInMemory(memory);
    }

    @Test
    void reactive_service_should_roll_back_only_after_the_tool_result_is_persisted_when_cancelled() throws Exception {

        SlowToolResultMemory memory = new SlowToolResultMemory();
        CompensableBooking booking = new CompensableBooking();
        CompletableFuture<ToolCompensatedEvent> compensated = new CompletableFuture<>();

        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(streamingBookingModel())
                .chatMemory(memory)
                .tools(booking)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) compensated::complete)
                .build();

        EventCollector collector = new EventCollector();
        assistant.chat("Book a hotel").subscribe(collector);

        assertThat(memory.toolResultPersistRequested.await(10, SECONDS)).isTrue();
        collector.subscription.cancel();
        memory.toolResultPersisted.complete(null);

        compensated.get(10, SECONDS);

        assertThat(booking.cancellations).hasValue(1);
        assertThat(memory.messages()).hasSize(3);
        assertThat(((ToolExecutionResultMessage) memory.messages().get(2)).text())
                .contains("rolled back");
    }

    @Test
    void reactive_service_should_roll_back_only_after_the_tool_result_is_persisted_when_cancelled_as_the_persist_starts()
            throws Exception {

        EventCollector collector = new EventCollector();
        SlowToolResultMemory memory = new SlowToolResultMemory() {

            @Override
            public CompletableFuture<Void> addAsync(List<ChatMessage> messages) {
                if (messages.stream().anyMatch(ToolExecutionResultMessage.class::isInstance)) {
                    collector.subscription.cancel();
                }
                return super.addAsync(messages);
            }
        };
        CompensableBooking booking = new CompensableBooking();
        CompletableFuture<ToolCompensatedEvent> compensated = new CompletableFuture<>();

        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(streamingBookingModel())
                .chatMemory(memory)
                .tools(booking)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) compensated::complete)
                .build();

        assistant.chat("Book a hotel").subscribe(collector);

        assertThat(memory.toolResultPersistRequested.await(10, SECONDS)).isTrue();
        memory.toolResultPersisted.complete(null);

        compensated.get(10, SECONDS);

        assertThat(booking.cancellations).hasValue(1);
        assertThat(memory.messages()).hasSize(3);
        assertThat(((ToolExecutionResultMessage) memory.messages().get(2)).text())
                .contains("rolled back");
    }

    @Test
    void token_stream_service_should_not_store_an_unanswered_tool_call_when_round_trips_are_exceeded() throws Exception {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(MockStreamingChatModel.thatResponds(
                        AiMessage.from(GET_TEMPERATURE), AiMessage.from(GET_TEMPERATURE)))
                .chatMemory(memory)
                .tools(new Weather())
                .maxToolCallingRoundTrips(1)
                .build();

        CompletableFuture<ChatResponse> completed = new CompletableFuture<>();
        assistant
                .chat("What is the temperature?")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start();

        assertThat(completed).failsWithin(10, SECONDS);
        assertThat(memory.messages()).hasSize(3);
        assertThat(memory.messages().get(2)).isInstanceOf(ToolExecutionResultMessage.class);
    }

    @Test
    void reactive_service_should_not_store_an_unanswered_tool_call_when_round_trips_are_exceeded() throws Exception {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(MockStreamingChatModel.thatResponds(
                        AiMessage.from(GET_TEMPERATURE), AiMessage.from(GET_TEMPERATURE)))
                .chatMemory(memory)
                .tools(new Weather())
                .maxToolCallingRoundTrips(1)
                .build();

        EventCollector collector = new EventCollector();
        assistant.chat("What is the temperature?").subscribe(collector);

        assertThat(collector.terminated).failsWithin(10, SECONDS);
        assertThat(memory.messages()).hasSize(3);
        assertThat(memory.messages().get(2)).isInstanceOf(ToolExecutionResultMessage.class);
    }

    private static List<ToolExecutionResultMessage> toolResultsIn(ChatMemory memory) {
        return memory.messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .toList();
    }

    @Test
    void sync_service_should_answer_every_tool_call_of_a_round_whose_tool_error_fails_the_invocation() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        MockChatModel model = MockChatModel.thatResponds(
                AiMessage.from(DIVIDE_WITH_INVALID_ARGUMENTS, GET_TEMPERATURE), AiMessage.from("Hello"));

        SyncAssistant assistant = AiServices.builder(SyncAssistant.class)
                .chatModel(model)
                .chatMemory(memory)
                .tools(new Calculator(), new Weather())
                .build();

        assertThatThrownBy(() -> assistant.chat("Divide one by two")).isInstanceOf(IllegalArgumentException.class);

        assertThat(memory.messages()).hasSize(4);
        assertThat(toolResultsIn(memory))
                .extracting(
                        ToolExecutionResultMessage::id, ToolExecutionResultMessage::text, ToolExecutionResultMessage::isError)
                .containsExactly(
                        tuple("call-5", DIVIDE_FAILED, true),
                        tuple("call-2", "Tool 'temperature' was not executed because tool 'divide' failed", true));

        // the next invocation of the same conversation sends a history that providers accept
        assistant.chat("Hi");
        List<ChatMessage> nextRequest = model.lastRequest().messages();
        assertThat(nextRequest)
                .extracting(ChatMessage::type)
                .containsExactly(
                        ChatMessageType.USER,
                        ChatMessageType.AI,
                        ChatMessageType.TOOL_EXECUTION_RESULT,
                        ChatMessageType.TOOL_EXECUTION_RESULT,
                        ChatMessageType.USER);
        assertThat(((AiMessage) nextRequest.get(1)).toolExecutionRequests())
                .extracting(ToolExecutionRequest::id)
                .containsExactly("call-5", "call-2");
        assertThat(List.of(nextRequest.get(2), nextRequest.get(3)))
                .extracting(message -> ((ToolExecutionResultMessage) message).id())
                .containsExactly("call-5", "call-2");
    }

    @Test
    void sync_service_should_keep_the_results_of_the_tools_that_ran_and_not_store_the_tool_error() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        SyncAssistant assistant = AiServices.builder(SyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(DIVIDE_FOUR_BY_TWO, DIVIDE_BY_ZERO)))
                .chatMemory(memory)
                .tools(new Calculator())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm())
                .build();

        assertThatThrownBy(() -> assistant.chat("Divide"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Division by zero");

        assertThat(toolResultsIn(memory))
                .extracting(
                        ToolExecutionResultMessage::id, ToolExecutionResultMessage::text, ToolExecutionResultMessage::isError)
                .containsExactly(tuple("call-3", "2.0", false), tuple("call-4", DIVIDE_FAILED, true));
    }

    @Test
    void sync_service_should_answer_a_call_of_a_tool_that_does_not_exist() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        SyncAssistant assistant = AiServices.builder(SyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(NON_EXISTENT_TOOL, GET_TEMPERATURE)))
                .chatMemory(memory)
                .tools(new Calculator(), new Weather())
                .build();

        assertThatThrownBy(() -> assistant.chat("Multiply")).hasMessageContaining("no such tool exists");

        assertThat(toolResultsIn(memory))
                .extracting(ToolExecutionResultMessage::id, ToolExecutionResultMessage::text)
                .containsExactly(
                        tuple("call-6", "Tool 'multiply' does not exist, so the request was stopped"),
                        tuple("call-2", "Tool 'temperature' was not executed because tool 'multiply' does not exist"));
    }

    @Test
    void async_service_should_answer_a_call_of_a_tool_that_does_not_exist() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(NON_EXISTENT_TOOL, DIVIDE_FOUR_BY_TWO)))
                .chatMemory(memory)
                .tools(new Calculator())
                .build();

        assertThat(assistant.chat("Multiply and divide")).failsWithin(10, SECONDS);

        assertThat(toolResultsIn(memory))
                .extracting(ToolExecutionResultMessage::id, ToolExecutionResultMessage::text)
                .containsExactly(
                        tuple("call-6", "Tool 'multiply' does not exist, so the request was stopped"),
                        tuple("call-3", "2.0"));
    }

    @Test
    void sync_service_should_store_the_rolled_back_results_of_a_round_whose_tool_error_fails_the_invocation() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        CompensableBooking booking = new CompensableBooking();

        SyncAssistant assistant = AiServices.builder(SyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(BOOK_HOTEL, DIVIDE_WITH_INVALID_ARGUMENTS)))
                .chatMemory(memory)
                .tools(booking, new Calculator())
                .compensateOnToolErrors(true)
                .build();

        assertThatThrownBy(() -> assistant.chat("Book a hotel")).isInstanceOf(IllegalArgumentException.class);

        assertThat(booking.cancellations).hasValue(1);
        assertThat(toolResultsIn(memory))
                .extracting(ToolExecutionResultMessage::id, ToolExecutionResultMessage::text)
                .containsExactly(
                        tuple("call-1", "Tool 'book' was executed successfully but was rolled back because tool 'divide' failed"),
                        tuple("call-5", DIVIDE_FAILED));
    }

    @Test
    void sync_service_should_rethrow_the_tool_error_when_the_results_of_the_failed_round_cannot_be_stored() {

        ChatMemory memory = new ThrowingBlockingToolResultMemory();

        SyncAssistant assistant = AiServices.builder(SyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(DIVIDE_WITH_INVALID_ARGUMENTS)))
                .chatMemory(memory)
                .tools(new Calculator())
                .build();

        assertThatThrownBy(() -> assistant.chat("Divide"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasSuppressedException(new IllegalStateException("store is down"));
    }

    @Test
    void token_stream_service_should_answer_every_tool_call_of_a_round_whose_tool_error_fails_the_invocation() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(
                        MockStreamingChatModel.thatResponds(AiMessage.from(DIVIDE_WITH_INVALID_ARGUMENTS, GET_TEMPERATURE)))
                .chatMemory(memory)
                .tools(new Calculator(), new Weather())
                .build();

        CompletableFuture<ChatResponse> completed = new CompletableFuture<>();
        assistant
                .chat("Divide one by two")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start();

        assertThat(completed)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(IllegalArgumentException.class);
        assertThat(memory.messages()).hasSize(4);
        assertThat(toolResultsIn(memory))
                .extracting(ToolExecutionResultMessage::id, ToolExecutionResultMessage::text)
                .containsExactly(
                        tuple("call-5", DIVIDE_FAILED),
                        tuple("call-2", "Tool 'temperature' was not executed because tool 'divide' failed"));
    }

    @Test
    void async_service_should_answer_every_tool_call_of_a_round_whose_tool_error_fails_the_invocation() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(DIVIDE_FOUR_BY_TWO, DIVIDE_BY_ZERO)))
                .chatMemory(memory)
                .tools(new Calculator())
                .build();

        assertThat(assistant.chat("Divide"))
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .withMessageContaining("Division by zero");
        assertThat(memory.messages()).hasSize(4);
        assertThat(toolResultsIn(memory))
                .extracting(
                        ToolExecutionResultMessage::id, ToolExecutionResultMessage::text, ToolExecutionResultMessage::isError)
                .containsExactly(tuple("call-3", "2.0", false), tuple("call-4", DIVIDE_FAILED, true));
    }

    @Test
    void async_service_should_fail_with_the_tool_error_when_the_results_of_the_failed_round_cannot_be_stored() {

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(DIVIDE_BY_ZERO)))
                .chatMemory(new ThrowingToolResultMemory())
                .tools(new Calculator())
                .build();

        CompletableFuture<Result<String>> future = assistant.chat("Divide");

        assertThat(future).failsWithin(10, SECONDS);
        assertThat(catchThrowable(future::join))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasSuppressedException(new IllegalStateException("store is down"));
    }

    @Test
    void reactive_service_should_answer_every_tool_call_of_a_round_whose_tool_error_fails_the_invocation() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(MockStreamingChatModel.thatResponds(AiMessage.from(DIVIDE_FOUR_BY_TWO, DIVIDE_BY_ZERO)))
                .chatMemory(memory)
                .tools(new Calculator())
                .build();

        EventCollector collector = new EventCollector();
        assistant.chat("Divide").subscribe(collector);

        assertThat(collector.terminated)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .withMessageContaining("Division by zero");
        assertThat(memory.messages()).hasSize(4);
        assertThat(toolResultsIn(memory))
                .extracting(
                        ToolExecutionResultMessage::id, ToolExecutionResultMessage::text, ToolExecutionResultMessage::isError)
                .containsExactly(tuple("call-3", "2.0", false), tuple("call-4", DIVIDE_FAILED, true));
    }

    @Test
    void reactive_service_should_fail_with_the_tool_error_when_the_results_of_the_failed_round_cannot_be_stored() {

        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(MockStreamingChatModel.thatResponds(AiMessage.from(DIVIDE_BY_ZERO)))
                .chatMemory(new ThrowingToolResultMemory())
                .tools(new Calculator())
                .build();

        EventCollector collector = new EventCollector();
        assistant.chat("Divide").subscribe(collector);

        assertThat(collector.terminated).failsWithin(10, SECONDS);
        assertThat(catchThrowable(collector.terminated::join))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasSuppressedException(new IllegalStateException("store is down"));
    }

    @Test
    void sync_service_should_rethrow_the_tool_error_when_the_result_of_another_tool_cannot_be_converted() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        SyncAssistant assistant = AiServices.builder(SyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(REPORT, DIVIDE_WITH_INVALID_ARGUMENTS)))
                .chatMemory(memory)
                .tools(new Reports(), new Calculator())
                .build();

        assertThatThrownBy(() -> assistant.chat("Report and divide"))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(error -> assertThat(error.getSuppressed())
                        .singleElement()
                        .satisfies(suppressed -> assertThat(suppressed).hasMessageContaining("the title cannot be read")));

        assertThat(toolResultsIn(memory))
                .extracting(ToolExecutionResultMessage::id, ToolExecutionResultMessage::text)
                .containsExactly(
                        tuple("call-7", "Tool 'report' failed, so the request was stopped"),
                        tuple("call-5", DIVIDE_FAILED));
    }

    @Test
    void async_service_should_fail_with_the_tool_error_when_the_result_of_another_tool_cannot_be_converted() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(REPORT, DIVIDE_BY_ZERO)))
                .chatMemory(memory)
                .tools(new Reports(), new Calculator())
                .build();

        CompletableFuture<Result<String>> future = assistant.chat("Report and divide");

        assertThat(future).failsWithin(10, SECONDS);
        assertThat(catchThrowable(future::join))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Division by zero");
        assertThat(toolResultsIn(memory))
                .extracting(ToolExecutionResultMessage::id, ToolExecutionResultMessage::text)
                .containsExactly(
                        tuple("call-7", "Tool 'report' failed, so the request was stopped"),
                        tuple("call-4", DIVIDE_FAILED));
    }

    @Test
    void reactive_service_should_fail_with_the_tool_error_when_the_result_of_another_tool_cannot_be_converted() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(MockStreamingChatModel.thatResponds(AiMessage.from(REPORT, DIVIDE_BY_ZERO)))
                .chatMemory(memory)
                .tools(new Reports(), new Calculator())
                .build();

        EventCollector collector = new EventCollector();
        assistant.chat("Report and divide").subscribe(collector);

        assertThat(collector.terminated).failsWithin(10, SECONDS);
        assertThat(catchThrowable(collector.terminated::join))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Division by zero");
        assertThat(toolResultsIn(memory))
                .extracting(ToolExecutionResultMessage::id, ToolExecutionResultMessage::text)
                .containsExactly(
                        tuple("call-7", "Tool 'report' failed, so the request was stopped"),
                        tuple("call-4", DIVIDE_FAILED));
    }

    @Test
    void sync_service_should_keep_the_error_text_a_handler_sent_to_the_llm_when_another_tool_ends_the_invocation() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        SyncAssistant assistant = AiServices.builder(SyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(FIND_ORDER, DIVIDE_BY_ZERO)))
                .chatMemory(memory)
                .tools(new Orders(), new Calculator())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm())
                .build();

        assertThatThrownBy(() -> assistant.chat("Find the order and divide"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(toolResultsIn(memory))
                .extracting(
                        ToolExecutionResultMessage::id,
                        ToolExecutionResultMessage::toolName,
                        ToolExecutionResultMessage::text,
                        ToolExecutionResultMessage::isError)
                .containsExactly(
                        tuple("call-8", "findOrder", "There is no order 42", true),
                        tuple("call-4", "divide", DIVIDE_FAILED, true));
    }

    @Test
    void async_service_should_keep_the_error_text_a_handler_sent_to_the_llm_when_another_tool_ends_the_invocation() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(FIND_ORDER, DIVIDE_BY_ZERO)))
                .chatMemory(memory)
                .tools(new Orders(), new Calculator())
                .build();

        assertThat(assistant.chat("Find the order and divide")).failsWithin(10, SECONDS);

        assertThat(toolResultsIn(memory))
                .extracting(
                        ToolExecutionResultMessage::id,
                        ToolExecutionResultMessage::toolName,
                        ToolExecutionResultMessage::text,
                        ToolExecutionResultMessage::isError)
                .containsExactly(
                        tuple("call-8", "findOrder", "There is no order 42", true),
                        tuple("call-4", "divide", DIVIDE_FAILED, true));
    }

    @Test
    void async_service_should_fail_only_once_the_results_of_the_failed_round_are_stored() throws Exception {

        SlowToolResultMemory memory = new SlowToolResultMemory();

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(DIVIDE_BY_ZERO)))
                .chatMemory(memory)
                .tools(new Calculator())
                .build();

        CompletableFuture<Result<String>> future = assistant.chat("Divide");

        assertThat(memory.toolResultPersistRequested.await(10, SECONDS)).isTrue();
        assertThat(future).isNotDone();
        memory.toolResultPersisted.complete(null);

        assertThat(future)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(toolResultsIn(memory))
                .extracting(ToolExecutionResultMessage::id, ToolExecutionResultMessage::text)
                .containsExactly(tuple("call-4", DIVIDE_FAILED));
    }

    @Test
    void async_service_should_fail_with_the_tool_error_when_storing_the_results_of_the_failed_round_fails_later()
            throws Exception {

        SlowToolResultMemory memory = new SlowToolResultMemory();

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(DIVIDE_BY_ZERO)))
                .chatMemory(memory)
                .tools(new Calculator())
                .build();

        CompletableFuture<Result<String>> future = assistant.chat("Divide");

        assertThat(memory.toolResultPersistRequested.await(10, SECONDS)).isTrue();
        memory.toolResultPersisted.completeExceptionally(new IllegalStateException("store is down"));

        assertThat(future).failsWithin(10, SECONDS);
        assertThat(catchThrowable(future::join))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasSuppressedException(new IllegalStateException("store is down"));
    }

    @Test
    void reactive_service_should_fail_only_once_the_results_of_the_failed_round_are_stored() throws Exception {

        SlowToolResultMemory memory = new SlowToolResultMemory();

        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(MockStreamingChatModel.thatResponds(AiMessage.from(DIVIDE_BY_ZERO)))
                .chatMemory(memory)
                .tools(new Calculator())
                .build();

        EventCollector collector = new EventCollector();
        assistant.chat("Divide").subscribe(collector);

        assertThat(memory.toolResultPersistRequested.await(10, SECONDS)).isTrue();
        assertThat(collector.terminated).isNotDone();
        memory.toolResultPersisted.complete(null);

        assertThat(collector.terminated)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(toolResultsIn(memory))
                .extracting(ToolExecutionResultMessage::id, ToolExecutionResultMessage::text)
                .containsExactly(tuple("call-4", DIVIDE_FAILED));
    }
}
