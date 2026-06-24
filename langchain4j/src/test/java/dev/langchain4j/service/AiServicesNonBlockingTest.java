package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.listener.AiServiceCompletedListener;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;

/**
 * Verifies that the asynchronous AI Service pipeline never performs a blocking call on the thread that
 * delivers model responses — policed by BlockHound. This covers both the {@code CompletableFuture} return
 * types (single-response path) and the reactive {@code Flow.Publisher} streaming path (whose stub emits all
 * events on the same policed delivery thread, so its tool loop, chat-memory writes, event firing and
 * round-to-round resubscription are policed too).
 * <p>
 * The stub model completes its futures (or emits its stream) on a dedicated thread (named
 * {@code ai-service-delivery}) that BlockHound treats as non-blocking. Because every downstream stage of the async pipeline runs on the
 * thread that completed the previous stage, the entire post-response pipeline — tool-loop bookkeeping,
 * chat-memory writes, tool result processing, output guardrails, output parsing, {@code Result} building,
 * event firing and the recursive next round — executes on that policed thread. Any hidden
 * {@code Future.get()}, sleep, or I/O in there fails the test deterministically, without a real API.
 * <p>
 * Each test enables a different AI Service feature so that each component crosses the policed pipeline.
 * Limitation: when tools execute concurrently, stages following the tool barrier run on the tool executor's
 * (unpoliced) threads until the next model response returns to the policed thread.
 * <p>
 * BlockHound is JVM-global, so recorded violations are shared static state; {@link #resetViolations()}
 * clears them before each test. This module runs tests concurrently by default (junit-platform.properties),
 * so this class forces sequential execution — otherwise the self-test's deliberately-blocking policed
 * thread would pollute the violation list of concurrently running tests.
 */
@Execution(ExecutionMode.SAME_THREAD)
class AiServicesNonBlockingTest {

    /**
     * Blocking calls BlockHound observed on the policed thread. Cleared before each test by
     * {@link #resetViolations()}; the pipeline tests expect none, the self-test expects one.
     */
    private static final List<Throwable> violations = new CopyOnWriteArrayList<>();

    private static final ExecutorService deliveryExecutor =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "ai-service-delivery"));

    // A non-policed pool that a blocking ChatMemoryStore offloads its (blocking) work to. Named distinctly so
    // BlockHound does not police it - the point is that the framework offloads memory I/O here and never runs it
    // on the policed delivery thread.
    private static final ExecutorService memoryWorker =
            Executors.newCachedThreadPool(runnable -> new Thread(runnable, "chat-memory-worker"));

    // A non-policed pool that a blocking guardrail offloads its (blocking) validation to via validateAsync.
    // Named distinctly so BlockHound does not police it - the point is that an I/O guardrail offloads its work
    // here and never runs it on the policed delivery thread.
    private static final ExecutorService guardrailWorker =
            Executors.newCachedThreadPool(runnable -> new Thread(runnable, "guardrail-worker"));

    @BeforeAll
    static void installBlockHound() {
        BlockHound.builder()
                .nonBlockingThreadPredicate(prev -> prev.or(thread -> thread.getName()
                        .startsWith("ai-service-delivery")))
                // Pool bookkeeping, not application blocking: idle workers park on the work queue (getTask),
                // exiting workers acquire the pool's lock to coordinate shutdown (processWorkerExit).
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "getTask")
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "processWorkerExit")
                // Record (don't throw): a thrown error on the delivery thread would fail the future, but
                // recording gives a precise assertion message with the offending stack trace.
                .blockingMethodCallback(method -> violations.add(new BlockingOperationError(method)))
                .install();
    }

    /**
     * Warms up the async pipeline on the delivery thread once, before any test asserts. The first time JSON
     * output parsing runs (Jackson, JSON-schema reflection, {@code MethodHandle} linkage, lazy class init), it
     * can {@code Unsafe.park} on a one-time initialization lock — which BlockHound flags even though it is not
     * application-level blocking and never recurs. Triggering it here (and clearing the recorded violations
     * via {@link #resetViolations()} before each test) removes that flakiness without masking real blocking:
     * any per-call blocking would still be caught by the per-test assertions on a fully warmed-up pipeline.
     */
    @BeforeAll
    static void warmUpDeliveryThread() throws Exception {
        AssistantReturningResult assistant = AiServices.builder(AssistantReturningResult.class)
                .chatModel(new NonBlockingChatModelStub(AiMessage.from("{\"name\": \"Warmup\"}")))
                .build();
        assistant.extractPerson("warm up the parsing pipeline").get(10, SECONDS);
    }

    @AfterAll
    static void shutDownDeliveryExecutor() {
        deliveryExecutor.shutdownNow();
        memoryWorker.shutdownNow();
        guardrailWorker.shutdownNow();
    }

    /**
     * A {@link ChatMemoryStore} whose synchronous methods block (simulating I/O). Its asynchronous methods offload
     * that blocking work to a non-policed worker pool. If the AI Service ever called the synchronous methods on
     * the policed delivery thread, BlockHound would flag it; using the async methods keeps the delivery thread
     * free. When a {@code gate} is supplied, the read blocks until it is released — used to prove the caller
     * thread is not blocked on memory.
     */
    static class BlockingChatMemoryStore implements ChatMemoryStore {

        private final InMemoryChatMemoryStore delegate = new InMemoryChatMemoryStore();
        private final Executor worker;
        private final CountDownLatch gate;

        BlockingChatMemoryStore(Executor worker) {
            this(worker, null);
        }

        BlockingChatMemoryStore(Executor worker, CountDownLatch gate) {
            this.worker = worker;
            this.gate = gate;
        }

        private void block() {
            try {
                if (gate != null) {
                    gate.await();
                } else {
                    Thread.sleep(20);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            block();
            return delegate.getMessages(memoryId);
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            block();
            delegate.updateMessages(memoryId, messages);
        }

        @Override
        public void deleteMessages(Object memoryId) {
            delegate.deleteMessages(memoryId);
        }

        @Override
        public CompletionStage<List<ChatMessage>> getMessagesAsync(Object memoryId) {
            return CompletableFuture.supplyAsync(() -> getMessages(memoryId), worker);
        }

        @Override
        public CompletionStage<Void> updateMessagesAsync(Object memoryId, List<ChatMessage> messages) {
            return CompletableFuture.runAsync(() -> updateMessages(memoryId, messages), worker);
        }

        @Override
        public CompletionStage<Void> deleteMessagesAsync(Object memoryId) {
            return CompletableFuture.runAsync(() -> deleteMessages(memoryId), worker);
        }
    }

    private static MessageWindowChatMemory blockingMemory(CountDownLatch gate) {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .chatMemoryStore(new BlockingChatMemoryStore(memoryWorker, gate))
                .build();
    }

    @Test
    void blocking_chat_memory_store_does_not_block_the_delivery_thread() throws Exception {

        NonBlockingChatModelStub chatModel = new NonBlockingChatModelStub(
                AiMessage.from(toolRequest("1", "currentTemperature")), AiMessage.from("42 degrees"));
        WeatherTools tools = new WeatherTools();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(blockingMemory(null))
                .tools(tools)
                .build();

        String answer = assistant.chat("What is the temperature in Munich?").get(10, SECONDS);

        assertThat(answer).isEqualTo("42 degrees");
        assertThat(tools.invocations).hasValue(1);
        assertNoBlockingCalls();
    }

    @Test
    void streaming_with_blocking_chat_memory_store_does_not_block_the_delivery_thread() throws Exception {

        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreamsOn(
                deliveryExecutor,
                AiMessage.from(toolRequest("1", "currentTemperature")),
                AiMessage.from("42 degrees"));
        WeatherTools tools = new WeatherTools();

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(blockingMemory(null))
                .tools(tools)
                .build();

        assertThat(subscribeAndAwait(assistant.chat("What is the temperature in Munich?")))
                .isEqualTo("42 degrees");
        assertThat(tools.invocations).hasValue(1);
        assertNoBlockingCalls();
    }

    @Test
    void chat_does_not_block_the_caller_thread_on_chat_memory() throws Exception {

        // The store's read blocks until the gate is released. If the caller thread assembled memory
        // synchronously, chat() would block here; instead it returns a not-yet-completed future immediately.
        CountDownLatch gate = new CountDownLatch(1);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(new NonBlockingChatModelStub(AiMessage.from("Berlin")))
                .chatMemory(blockingMemory(gate))
                .build();

        CompletableFuture<String> future = assistant.chat("What is the capital of Germany?");

        assertThat(future).isNotDone();

        gate.countDown();

        assertThat(future.get(10, SECONDS)).isEqualTo("Berlin");
    }

    @BeforeEach
    void resetViolations() {
        violations.clear();
    }

    /**
     * Completes responses on the policed delivery thread, with a small delay so that the async pipeline's
     * stages are attached before the future completes — ensuring they run on the policed (completing)
     * thread rather than the caller's.
     */
    static class NonBlockingChatModelStub implements ChatModel {

        private final Queue<AiMessage> responses;

        NonBlockingChatModelStub(AiMessage... aiMessages) {
            this.responses = new ConcurrentLinkedQueue<>(List.of(aiMessages));
        }

        @Override
        public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
            return CompletableFuture.supplyAsync(
                    () -> ChatResponse.builder()
                            .aiMessage(responses.poll())
                            .metadata(ChatResponseMetadata.builder().build())
                            .build(),
                    CompletableFuture.delayedExecutor(10, MILLISECONDS, deliveryExecutor));
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            throw new AssertionError("Blocking chat() must not be called "
                    + "when the AI Service method returns a CompletableFuture");
        }
    }

    interface Assistant {

        CompletableFuture<String> chat(String userMessage);
    }

    @Test
    void plain_chat_does_not_block_the_delivery_thread() throws Exception {

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(new NonBlockingChatModelStub(AiMessage.from("Berlin")))
                .build();

        Thread callerThread = Thread.currentThread();
        AtomicReference<Thread> completionThread = new AtomicReference<>();

        String answer = assistant
                .chat("What is the capital of Germany?")
                .whenComplete((response, error) -> completionThread.set(Thread.currentThread()))
                .get(10, SECONDS);

        assertThat(answer).isEqualTo("Berlin");
        assertThat(completionThread.get()).isNotNull().isNotEqualTo(callerThread);
        assertNoBlockingCalls();
    }

    static class WeatherTools {

        final AtomicInteger invocations = new AtomicInteger();

        @Tool
        String currentTemperature(String city) {
            invocations.incrementAndGet();
            return "42";
        }

        @Tool
        String currentHumidity(String city) {
            invocations.incrementAndGet();
            return "69";
        }
    }

    private static ToolExecutionRequest toolRequest(String id, String toolName) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(toolName)
                .arguments("{\"arg0\": \"Munich\"}")
                .build();
    }

    @Test
    void tool_loop_with_multiple_rounds_and_memory_does_not_block_the_delivery_thread() throws Exception {

        NonBlockingChatModelStub chatModel = new NonBlockingChatModelStub(
                AiMessage.from(toolRequest("1", "currentTemperature")),
                AiMessage.from(toolRequest("2", "currentHumidity")),
                AiMessage.from("42 degrees, 69 percent"));
        WeatherTools tools = new WeatherTools();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(tools)
                .build();

        String answer = assistant.chat("What is the weather in Munich?").get(10, SECONDS);

        assertThat(answer).isEqualTo("42 degrees, 69 percent");
        assertThat(tools.invocations).hasValue(2);
        assertNoBlockingCalls();
    }

    @Test
    void concurrent_tool_execution_does_not_block_the_delivery_thread() throws Exception {

        NonBlockingChatModelStub chatModel = new NonBlockingChatModelStub(
                AiMessage.from(toolRequest("1", "currentTemperature"), toolRequest("2", "currentHumidity")),
                AiMessage.from("42 degrees, 69 percent"));
        WeatherTools tools = new WeatherTools();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .executeToolsConcurrently()
                .build();

        String answer = assistant.chat("What is the weather in Munich?").get(10, SECONDS);

        assertThat(answer).isEqualTo("42 degrees, 69 percent");
        assertThat(tools.invocations).hasValue(2);
        assertNoBlockingCalls();
    }

    static class AsyncWeatherTools {

        final AtomicInteger invocations = new AtomicInteger();

        @Tool
        CompletableFuture<String> currentTemperature(String city) {
            invocations.incrementAndGet();
            // completes later, on an unpoliced thread; while it is pending, nothing may block
            // the policed delivery thread
            return CompletableFuture.supplyAsync(() -> "42", CompletableFuture.delayedExecutor(50, MILLISECONDS));
        }

        @Tool
        CompletableFuture<String> currentHumidity(String city) {
            invocations.incrementAndGet();
            return CompletableFuture.supplyAsync(() -> "69", CompletableFuture.delayedExecutor(50, MILLISECONDS));
        }
    }

    @Test
    void async_tool_does_not_block_the_delivery_thread() throws Exception {

        NonBlockingChatModelStub chatModel = new NonBlockingChatModelStub(
                AiMessage.from(toolRequest("1", "currentTemperature")), AiMessage.from("42 degrees"));
        AsyncWeatherTools tools = new AsyncWeatherTools();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();

        String answer = assistant.chat("What is the temperature in Munich?").get(10, SECONDS);

        assertThat(answer).isEqualTo("42 degrees");
        assertThat(tools.invocations).hasValue(1);
        assertNoBlockingCalls();
    }

    @Test
    void concurrent_async_tools_do_not_block_the_delivery_thread() throws Exception {

        NonBlockingChatModelStub chatModel = new NonBlockingChatModelStub(
                AiMessage.from(toolRequest("1", "currentTemperature"), toolRequest("2", "currentHumidity")),
                AiMessage.from("42 degrees, 69 percent"));
        AsyncWeatherTools tools = new AsyncWeatherTools();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .executeToolsConcurrently()
                .build();

        String answer = assistant.chat("What is the weather in Munich?").get(10, SECONDS);

        assertThat(answer).isEqualTo("42 degrees, 69 percent");
        assertThat(tools.invocations).hasValue(2);
        assertNoBlockingCalls();
    }

    static class MixedWeatherTools {

        final AtomicInteger invocations = new AtomicInteger();

        @Tool
        CompletableFuture<String> currentTemperature(String city) {
            invocations.incrementAndGet();
            return CompletableFuture.supplyAsync(() -> "42", CompletableFuture.delayedExecutor(50, MILLISECONDS));
        }

        @Tool
        String currentHumidity(String city) {
            invocations.incrementAndGet();
            return "69";
        }
    }

    @Test
    void mixed_async_and_sync_tools_do_not_block_the_delivery_thread() throws Exception {

        NonBlockingChatModelStub chatModel = new NonBlockingChatModelStub(
                AiMessage.from(toolRequest("1", "currentTemperature"), toolRequest("2", "currentHumidity")),
                AiMessage.from("42 degrees, 69 percent"));
        MixedWeatherTools tools = new MixedWeatherTools();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .executeToolsConcurrently()
                .build();

        String answer = assistant.chat("What is the weather in Munich?").get(10, SECONDS);

        assertThat(answer).isEqualTo("42 degrees, 69 percent");
        assertThat(tools.invocations).hasValue(2);
        assertNoBlockingCalls();
    }

    static class Person {

        String name;
    }

    interface AssistantReturningResult {

        CompletableFuture<Result<Person>> extractPerson(String text);
    }

    @Test
    void result_with_pojo_parsing_does_not_block_the_delivery_thread() throws Exception {

        AssistantReturningResult assistant = AiServices.builder(AssistantReturningResult.class)
                .chatModel(new NonBlockingChatModelStub(AiMessage.from("{\"name\": \"Klaus\"}")))
                .build();

        Result<Person> result = assistant.extractPerson("My name is Klaus").get(10, SECONDS);

        assertThat(result.content().name).isEqualTo("Klaus");
        assertNoBlockingCalls();
    }

    public static class PassingOutputGuardrail implements OutputGuardrail {

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            // Non-blocking (CPU-only) guardrail: wrap the synchronous validation in a completed stage.
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    interface GuardedAssistant {

        @OutputGuardrails(PassingOutputGuardrail.class)
        CompletableFuture<String> chat(String userMessage);
    }

    @Test
    void output_guardrails_do_not_block_the_delivery_thread() throws Exception {

        GuardedAssistant assistant = AiServices.builder(GuardedAssistant.class)
                .chatModel(new NonBlockingChatModelStub(AiMessage.from("Berlin")))
                .build();

        String answer = assistant.chat("What is the capital of Germany?").get(10, SECONDS);

        assertThat(answer).isEqualTo("Berlin");
        assertNoBlockingCalls();
    }

    /**
     * An output guardrail whose validation blocks (simulating a remote moderation/PII call). Because it does
     * blocking I/O, it overrides {@link #validateAsync} to offload that work to a non-policed worker pool, keeping
     * the model-delivery thread free — exactly what an I/O guardrail must do on the non-blocking AI Service paths.
     */
    public static class BlockingOutputGuardrail implements OutputGuardrail {

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return success();
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.supplyAsync(() -> validate(request), guardrailWorker);
        }
    }

    /**
     * An output guardrail that reprompts the model once (when the response still contains "retry") and then
     * passes. Its (blocking) validation is offloaded via {@link #validateAsync}; the reprompt re-calls the model
     * via {@link ChatExecutor#executeAsync(java.util.List)}. Neither must touch the delivery thread.
     */
    public static class RepromptOnceOutputGuardrail implements OutputGuardrail {

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (responseFromLLM.text().contains("retry")) {
                return reprompt("Unacceptable answer", "Please answer the question properly");
            }
            return success();
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.supplyAsync(() -> validate(request), guardrailWorker);
        }
    }

    interface BlockingGuardedAssistant {

        @OutputGuardrails(BlockingOutputGuardrail.class)
        CompletableFuture<String> chat(String userMessage);
    }

    interface BlockingGuardedStreamingAssistant {

        @OutputGuardrails(BlockingOutputGuardrail.class)
        Flow.Publisher<String> chat(String userMessage);
    }

    interface RepromptGuardedAssistant {

        @OutputGuardrails(RepromptOnceOutputGuardrail.class)
        CompletableFuture<String> chat(String userMessage);
    }

    /**
     * Delivers the first response asynchronously on the policed delivery thread (like
     * {@link NonBlockingChatModelStub}), but — unlike it — also implements the blocking {@link #doChat} used by
     * output-guardrail reprompts. Reprompts run on a virtual thread off the delivery thread, so blocking there
     * is acceptable.
     */
    static class RepromptingChatModelStub implements ChatModel {

        private final Queue<AiMessage> responses;

        RepromptingChatModelStub(AiMessage... aiMessages) {
            this.responses = new ConcurrentLinkedQueue<>(List.of(aiMessages));
        }

        @Override
        public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
            return CompletableFuture.supplyAsync(
                    this::nextResponse, CompletableFuture.delayedExecutor(10, MILLISECONDS, deliveryExecutor));
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return nextResponse();
        }

        private ChatResponse nextResponse() {
            return ChatResponse.builder()
                    .aiMessage(responses.poll())
                    .metadata(ChatResponseMetadata.builder().build())
                    .build();
        }
    }

    @Test
    void cf_blocking_output_guardrail_does_not_block_the_delivery_thread() throws Exception {

        BlockingGuardedAssistant assistant = AiServices.builder(BlockingGuardedAssistant.class)
                .chatModel(new NonBlockingChatModelStub(AiMessage.from("Berlin")))
                .build();

        String answer = assistant.chat("What is the capital of Germany?").get(10, SECONDS);

        assertThat(answer).isEqualTo("Berlin");
        assertNoBlockingCalls();
    }

    @Test
    void streaming_blocking_output_guardrail_does_not_block_the_delivery_thread() throws Exception {

        BlockingGuardedStreamingAssistant assistant = AiServices.builder(BlockingGuardedStreamingAssistant.class)
                .streamingChatModel(StreamingEventChatModelMock.thatStreamsOn(deliveryExecutor, AiMessage.from("Berlin")))
                .build();

        assertThat(subscribeAndAwait(assistant.chat("What is the capital of Germany?")))
                .isEqualTo("Berlin");
        assertNoBlockingCalls();
    }

    @Test
    void cf_output_guardrail_reprompt_does_not_block_the_delivery_thread() throws Exception {

        RepromptingChatModelStub model =
                new RepromptingChatModelStub(AiMessage.from("please retry"), AiMessage.from("Berlin"));

        RepromptGuardedAssistant assistant = AiServices.builder(RepromptGuardedAssistant.class)
                .chatModel(model)
                .build();

        String answer = assistant.chat("What is the capital of Germany?").get(10, SECONDS);

        assertThat(answer).isEqualTo("Berlin");
        assertNoBlockingCalls();
    }

    /**
     * An input guardrail whose validation blocks (simulating a remote moderation/PII call); like its output
     * sibling it offloads via {@link #validateAsync} to a non-policed worker so the caller and delivery threads
     * stay free.
     */
    public static class BlockingInputGuardrail implements InputGuardrail {

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return success();
        }

        @Override
        public CompletableFuture<InputGuardrailResult> validateAsync(InputGuardrailRequest request) {
            return CompletableFuture.supplyAsync(() -> validate(request), guardrailWorker);
        }
    }

    /**
     * An input guardrail whose validation blocks until a shared {@code gate} is released, used to prove the
     * caller thread is not blocked while input guardrails run.
     */
    public static class GatedInputGuardrail implements InputGuardrail {

        static volatile CountDownLatch gate;

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return success();
        }

        @Override
        public CompletableFuture<InputGuardrailResult> validateAsync(InputGuardrailRequest request) {
            return CompletableFuture.supplyAsync(() -> validate(request), guardrailWorker);
        }
    }

    interface BlockingInputGuardedAssistant {

        @InputGuardrails(BlockingInputGuardrail.class)
        CompletableFuture<String> chat(String userMessage);
    }

    interface GatedInputGuardedAssistant {

        @InputGuardrails(GatedInputGuardrail.class)
        CompletableFuture<String> chat(String userMessage);
    }

    @Test
    void cf_blocking_input_guardrail_does_not_block_the_delivery_thread() throws Exception {

        BlockingInputGuardedAssistant assistant = AiServices.builder(BlockingInputGuardedAssistant.class)
                .chatModel(new NonBlockingChatModelStub(AiMessage.from("Berlin")))
                .build();

        String answer = assistant.chat("What is the capital of Germany?").get(10, SECONDS);

        assertThat(answer).isEqualTo("Berlin");
        assertNoBlockingCalls();
    }

    @Test
    void chat_does_not_block_the_caller_thread_on_input_guardrails() throws Exception {

        // The input guardrail blocks until the gate is released. If input guardrails ran on the caller thread,
        // chat() would block here; instead it returns a not-yet-completed future immediately.
        CountDownLatch gate = new CountDownLatch(1);
        GatedInputGuardrail.gate = gate;

        GatedInputGuardedAssistant assistant = AiServices.builder(GatedInputGuardedAssistant.class)
                .chatModel(new NonBlockingChatModelStub(AiMessage.from("Berlin")))
                .build();

        CompletableFuture<String> future = assistant.chat("What is the capital of Germany?");

        assertThat(future).isNotDone();

        gate.countDown();

        assertThat(future.get(10, SECONDS)).isEqualTo("Berlin");
        assertNoBlockingCalls();
    }

    /**
     * An input guardrail that signals when its (offloaded) validation has started and then blocks until released,
     * so a test can cancel the AI Service future while the guardrail is in flight.
     */
    public static class CancellableInputGuardrail implements InputGuardrail {

        static volatile CountDownLatch entered;
        static volatile CountDownLatch release;

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return success();
        }

        @Override
        public CompletableFuture<InputGuardrailResult> validateAsync(InputGuardrailRequest request) {
            return CompletableFuture.supplyAsync(() -> validate(request), guardrailWorker);
        }
    }

    interface CancellableInputGuardedAssistant {

        @InputGuardrails(CancellableInputGuardrail.class)
        CompletableFuture<String> chat(String userMessage);
    }

    @Test
    void cancelling_the_future_during_input_guardrails_skips_the_model_call() throws Exception {

        // Cancelling the returned future while an input guardrail is in flight must short-circuit the pipeline:
        // the model is never called. (The guardrail validation itself runs to completion — best-effort, like a
        // started tool.)
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CancellableInputGuardrail.entered = entered;
        CancellableInputGuardrail.release = release;

        AtomicInteger modelCalls = new AtomicInteger();
        ChatModel chatModel = new ChatModel() {

            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
                modelCalls.incrementAndGet();
                return CompletableFuture.completedFuture(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Berlin"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build());
            }

            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                throw new AssertionError("blocking chat() must not be called");
            }
        };

        CancellableInputGuardedAssistant assistant = AiServices.builder(CancellableInputGuardedAssistant.class)
                .chatModel(chatModel)
                .build();

        CompletableFuture<String> future = assistant.chat("What is the capital of Germany?");

        assertThat(entered.await(5, SECONDS)).as("input guardrail should start").isTrue();
        future.cancel(true);
        release.countDown();

        Thread.sleep(200);
        assertThat(future).isCancelled();
        assertThat(modelCalls).as("model must not be called after cancellation").hasValue(0);
    }

    static class CountingCompletedListener implements AiServiceCompletedListener {

        final AtomicInteger count = new AtomicInteger();

        @Override
        public void onEvent(AiServiceCompletedEvent event) {
            count.incrementAndGet();
        }
    }

    @Test
    void event_listeners_do_not_block_the_delivery_thread() throws Exception {

        CountingCompletedListener listener = new CountingCompletedListener();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(new NonBlockingChatModelStub(AiMessage.from("Berlin")))
                .registerListeners(List.of(listener))
                .build();

        String answer = assistant.chat("What is the capital of Germany?").get(10, SECONDS);

        assertThat(answer).isEqualTo("Berlin");
        assertThat(listener.count).hasValue(1);
        assertNoBlockingCalls();
    }

    interface StreamingAssistant {

        Flow.Publisher<String> chat(String userMessage);
    }

    @Test
    void streaming_plain_chat_does_not_block_the_delivery_thread() throws Exception {

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingEventChatModelMock.thatStreamsOn(deliveryExecutor, AiMessage.from("Berlin")))
                .build();

        assertThat(subscribeAndAwait(assistant.chat("What is the capital of Germany?")))
                .isEqualTo("Berlin");
        assertNoBlockingCalls();
    }

    @Test
    void streaming_tool_loop_does_not_block_the_delivery_thread() throws Exception {

        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreamsOn(
                deliveryExecutor,
                AiMessage.from(toolRequest("1", "currentTemperature")),
                AiMessage.from("42 degrees"));
        WeatherTools tools = new WeatherTools();

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(tools)
                .build();

        assertThat(subscribeAndAwait(assistant.chat("What is the temperature in Munich?")))
                .isEqualTo("42 degrees");
        assertThat(tools.invocations).hasValue(1);
        assertNoBlockingCalls();
    }

    @Test
    void streaming_async_tool_does_not_block_the_delivery_thread() throws Exception {

        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreamsOn(
                deliveryExecutor,
                AiMessage.from(toolRequest("1", "currentTemperature")),
                AiMessage.from("42 degrees"));
        AsyncWeatherTools tools = new AsyncWeatherTools();

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .tools(tools)
                .build();

        assertThat(subscribeAndAwait(assistant.chat("What is the temperature in Munich?")))
                .isEqualTo("42 degrees");
        assertThat(tools.invocations).hasValue(1);
        assertNoBlockingCalls();
    }

    private static String subscribeAndAwait(Flow.Publisher<String> publisher) throws InterruptedException {
        List<String> items = new CopyOnWriteArrayList<>();
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

        assertThat(latch.await(10, SECONDS)).isTrue();
        assertThat(error.get()).isNull();
        return String.join("", items);
    }

    /**
     * Sanity-checks the harness itself: a blocking call on a policed ({@code ai-service-delivery*}) thread
     * MUST be recorded. Guarantees the tests above cannot pass vacuously if BlockHound ever stopped
     * policing the delivery thread.
     */
    @Test
    void blockHound_detects_blocking_on_the_policed_thread() throws Exception {
        Thread thread = new Thread(
                () -> {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                },
                "ai-service-delivery-selftest");
        thread.start();
        thread.join(SECONDS.toMillis(5));

        assertThat(violations)
                .as("BlockHound must flag a blocking call on a policed thread")
                .isNotEmpty();
    }

    private static void assertNoBlockingCalls() {
        assertThat(violations)
                .as("BlockHound detected blocking calls on the model-response delivery thread "
                        + "- see stack trace(s) below")
                .isEmpty();
    }
}
