package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.observability.api.event.CompensationReason;
import dev.langchain4j.observability.api.event.ToolCompensatedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.AiServiceCancelledListener;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
import dev.langchain4j.observability.api.listener.ToolCompensatedEventListener;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolServiceContext;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import dev.langchain4j.exception.ToolExecutionException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Compensation on tool errors works the same way in every AI Service mode: when a tool error is sent to the LLM,
 * when it fails the invocation, and when the invocation is cancelled, the tools that succeeded and have a
 * compensating action are rolled back.
 */
class AiServiceToolCompensationTest {

    private static final ToolExecutionRequest CREDIT = ToolExecutionRequest.builder()
            .id("1")
            .name("credit")
            .arguments("{\"arg0\": \"Mario\", \"arg1\": 100.0}")
            .build();

    private static final ToolExecutionRequest WITHDRAW = ToolExecutionRequest.builder()
            .id("2")
            .name("withdraw")
            .arguments("{\"arg0\": \"Mario\", \"arg1\": 100.0}")
            .build();

    private static final ToolExecutionRequest SECOND_CREDIT = ToolExecutionRequest.builder()
            .id("3")
            .name("credit")
            .arguments("{\"arg0\": \"Mario\", \"arg1\": 50.0}")
            .build();

    private static final ToolExecutionRequest CREDIT_FOR_LUIGI = ToolExecutionRequest.builder()
            .id("6")
            .name("credit")
            .arguments("{\"arg0\": \"Luigi\", \"arg1\": 25.0}")
            .build();

    private static final ToolExecutionRequest WITHDRAW_WITH_INVALID_ARGUMENTS = ToolExecutionRequest.builder()
            .id("2")
            .name("withdraw")
            .arguments("{ invalid json }")
            .build();

    private static final ToolExecutionRequest RESERVE = ToolExecutionRequest.builder()
            .id("4")
            .name("reserve")
            .arguments("{\"arg0\": \"Mario\", \"arg1\": 100.0}")
            .build();

    private static final String CREDIT_ROLLED_BACK =
            "Tool 'credit' was executed successfully but was rolled back because tool 'withdraw' failed";

    private static final String CREDIT_ROLLED_BACK_ON_CANCELLATION =
            "Tool 'credit' was executed successfully but was rolled back because the invocation was cancelled";

    private static final String RESERVE_ROLLED_BACK =
            "Tool 'reserve' was executed successfully but was rolled back because tool 'withdraw' failed";

    private static final String CREDIT_ROLLBACK_FAILED = "Tool 'credit' was executed successfully. "
            + "It had to be rolled back because tool 'withdraw' failed, "
            + "but the rollback failed, so its effects may still be in place";

    private static final String CREDIT_ROLLBACK_FAILED_ON_CANCELLATION = "Tool 'credit' was executed successfully. "
            + "It had to be rolled back because the invocation was cancelled, "
            + "but the rollback failed, so its effects may still be in place";

    static class InsufficientFundsException extends RuntimeException {

        InsufficientFundsException() {
            super("insufficient funds");
        }
    }

    static class Bank {

        final List<String> calls = new CopyOnWriteArrayList<>();
        volatile InsufficientFundsException thrown;
        volatile Thread uncreditThread;

        @Tool
        String credit(String name, double amount) {
            calls.add("credit:" + name);
            return "credited";
        }

        @CompensateFor("credit")
        void uncredit(String name, double amount) {
            uncreditThread = Thread.currentThread();
            calls.add("uncredit:" + name);
        }

        @Tool
        String reserve(String name, double amount) {
            calls.add("reserve:" + name);
            return "reserved";
        }

        @CompensateFor("reserve")
        void unreserve(String name, double amount) {
            calls.add("unreserve:" + name);
        }

        @Tool
        String withdraw(String name, double amount) {
            calls.add("withdraw:" + name);
            thrown = new InsufficientFundsException();
            throw thrown;
        }
    }

    // the first rollback of credit fails, the next ones work
    static class BankWithFirstUncreditFailing {

        final List<String> calls = new CopyOnWriteArrayList<>();
        final IllegalStateException uncreditFailure = new IllegalStateException("uncredit failed");

        @Tool
        String credit(String name, double amount) {
            calls.add("credit:" + name);
            return "credited";
        }

        @CompensateFor("credit")
        void uncredit(String name, double amount) {
            calls.add("uncredit:" + name);
            if (calls.stream().filter(call -> call.startsWith("uncredit")).count() == 1) {
                throw uncreditFailure;
            }
        }

        @Tool
        String withdraw(String name, double amount) {
            calls.add("withdraw:" + name);
            throw new InsufficientFundsException();
        }
    }

    // the rollback of credit fails, in a compensating action that takes the ToolExecution
    static class BankWithFailingUncreditTakingTheToolExecution {

        final IllegalStateException uncreditFailure = new IllegalStateException("uncredit failed");
        volatile InsufficientFundsException thrown;

        @Tool
        String credit(String name, double amount) {
            return "credited";
        }

        @CompensateFor("credit")
        void uncredit(ToolExecution toolExecution) {
            throw uncreditFailure;
        }

        @Tool
        String withdraw(String name, double amount) {
            thrown = new InsufficientFundsException();
            throw thrown;
        }
    }

    // the rollback of credit fails, in a compensating action that returns a failed future
    static class BankWithUncreditReturningAFailedFuture {

        final IllegalStateException uncreditFailure = new IllegalStateException("uncredit failed");
        volatile InsufficientFundsException thrown;

        @Tool
        String credit(String name, double amount) {
            return "credited";
        }

        @CompensateFor("credit")
        CompletableFuture<Void> uncredit(String name, double amount) {
            return CompletableFuture.failedFuture(uncreditFailure);
        }

        @Tool
        String withdraw(String name, double amount) {
            thrown = new InsufficientFundsException();
            throw thrown;
        }
    }

    // the rollback of credit fails with a ToolExecutionException that has a cause and an error code, in a compensating
    // action that takes the ToolExecution and returns a failed future
    static class BankWithUncreditTakingTheToolExecutionFailingWithAToolExecutionException {

        final ToolExecutionException uncreditFailure =
                new ToolExecutionException(new IllegalStateException("ledger is down"), 42);
        volatile InsufficientFundsException thrown;

        @Tool
        String credit(String name, double amount) {
            return "credited";
        }

        @CompensateFor("credit")
        CompletableFuture<Void> uncredit(ToolExecution toolExecution) {
            return CompletableFuture.failedFuture(uncreditFailure);
        }

        @Tool
        String withdraw(String name, double amount) {
            thrown = new InsufficientFundsException();
            throw thrown;
        }
    }

    // the rollback of credit fails, in a compensating action that takes the ToolExecution and returns a type that a
    // CompletableFutureAdapter adapts
    static class BankWithUncreditTakingTheToolExecutionReturningAFailedFutureBox {

        final IllegalStateException uncreditFailure = new IllegalStateException("uncredit failed");
        volatile InsufficientFundsException thrown;

        @Tool
        String credit(String name, double amount) {
            return "credited";
        }

        @CompensateFor("credit")
        FutureBox<Void> uncredit(ToolExecution toolExecution) {
            return new FutureBox<>(CompletableFuture.failedFuture(uncreditFailure));
        }

        @Tool
        String withdraw(String name, double amount) {
            thrown = new InsufficientFundsException();
            throw thrown;
        }
    }

    // the rollback of credit takes the ToolExecution and finishes later, in a type that a CompletableFutureAdapter adapts
    static class BankWithLateUncreditTakingTheToolExecution {

        final List<String> calls = new CopyOnWriteArrayList<>();

        @Tool
        String credit(String name, double amount) {
            calls.add("credit:" + name);
            return "credited";
        }

        @CompensateFor("credit")
        FutureBox<Void> uncredit(ToolExecution toolExecution) {
            return new FutureBox<>(CompletableFuture.runAsync(
                    () -> calls.add("uncredit:" + toolExecution.request().id()),
                    CompletableFuture.delayedExecutor(300, MILLISECONDS)));
        }

        @Tool
        String withdraw(String name, double amount) {
            calls.add("withdraw:" + name);
            throw new InsufficientFundsException();
        }
    }

    // the rollback of credit fails, and reserve runs until it is released
    static class BankWithFailingUncreditAndSlowReserve {

        final List<String> calls = new CopyOnWriteArrayList<>();
        final IllegalStateException uncreditFailure = new IllegalStateException("uncredit failed");
        final CountDownLatch reserveStarted = new CountDownLatch(1);
        final CountDownLatch releaseReserve = new CountDownLatch(1);

        @Tool
        String credit(String name, double amount) {
            calls.add("credit:" + name);
            return "credited";
        }

        @CompensateFor("credit")
        void uncredit(String name, double amount) {
            calls.add("uncredit:" + name);
            throw uncreditFailure;
        }

        @Tool
        String reserve(String name, double amount) {
            calls.add("reserve:" + name);
            reserveStarted.countDown();
            try {
                releaseReserve.await(10, SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "reserved";
        }

        @CompensateFor("reserve")
        void unreserve(String name, double amount) {
            calls.add("unreserve:" + name);
        }
    }

    // the rollback of credit fails, the rollback of reserve works
    static class BankWithFailingUncredit {

        final List<String> calls = new CopyOnWriteArrayList<>();
        final IllegalStateException uncreditFailure = new IllegalStateException("uncredit failed");
        volatile InsufficientFundsException thrown;

        @Tool
        String reserve(String name, double amount) {
            calls.add("reserve:" + name);
            return "reserved";
        }

        @CompensateFor("reserve")
        void unreserve(String name, double amount) {
            calls.add("unreserve:" + name);
        }

        @Tool
        String credit(String name, double amount) {
            calls.add("credit:" + name);
            return "credited";
        }

        @CompensateFor("credit")
        void uncredit(String name, double amount) {
            calls.add("uncredit:" + name);
            throw uncreditFailure;
        }

        @Tool
        String withdraw(String name, double amount) {
            calls.add("withdraw:" + name);
            thrown = new InsufficientFundsException();
            throw thrown;
        }
    }

    // cancels the invocation while it runs, through the handle of the round it was requested in
    static class CancellingBank {

        final List<String> calls = new CopyOnWriteArrayList<>();
        volatile StreamingHandle streamingHandle;

        @Tool
        String credit(String name, double amount) {
            calls.add("credit:" + name);
            streamingHandle.cancel();
            calls.add("credit finished");
            return "credited";
        }

        @CompensateFor("credit")
        void uncredit(String name, double amount) {
            calls.add("uncredit:" + name);
        }

        @Tool
        String withdraw(String name, double amount) {
            calls.add("withdraw:" + name);
            return "withdrawn";
        }
    }

    static class SlowBank {

        final List<String> calls = new CopyOnWriteArrayList<>();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Tool
        String credit(String name, double amount) {
            calls.add("credit:" + name);
            started.countDown();
            try {
                release.await(10, SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            calls.add("credit finished");
            return "credited";
        }

        @CompensateFor("credit")
        void uncredit(String name, double amount) {
            calls.add("uncredit:" + name);
        }
    }

    // a store with blocking methods only, as many custom stores are
    static class SyncOnlyMemory implements ChatMemory {

        private final ChatMemory delegate = MessageWindowChatMemory.withMaxMessages(20);

        @Override
        public Object id() {
            return delegate.id();
        }

        @Override
        public void add(ChatMessage message) {
            delegate.add(message);
        }

        @Override
        public void set(Iterable<ChatMessage> messages) {
            delegate.set(messages);
        }

        @Override
        public List<ChatMessage> messages() {
            return delegate.messages();
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }

    // a store that cannot be written to while it is being rolled back
    static class MemoryFailingOnRewrite implements ChatMemory {

        private final ChatMemory delegate = MessageWindowChatMemory.withMaxMessages(20);

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
            return delegate.addAsync(messages);
        }

        @Override
        public CompletableFuture<List<ChatMessage>> messagesAsync() {
            return delegate.messagesAsync();
        }

        @Override
        public void set(Iterable<ChatMessage> messages) {
            throw new IllegalStateException("store is down");
        }

        @Override
        public CompletableFuture<Void> setAsync(List<ChatMessage> messages) {
            return CompletableFuture.failedFuture(new IllegalStateException("store is down"));
        }
    }

    enum SavingFailure {
        READING_THE_SAVED_RESULTS,
        ADDING_THE_SECOND_RESULT
    }

    // fails while the results of a round are saved, once the first one is added: it cannot add the next result, or it
    // cannot be read until the tools are rolled back
    static class MemoryFailingAfterTheFirstToolResult extends SyncOnlyMemory {

        private final SavingFailure failure;
        private final BooleanSupplier rolledBack;

        MemoryFailingAfterTheFirstToolResult(SavingFailure failure, BooleanSupplier rolledBack) {
            this.failure = failure;
            this.rolledBack = rolledBack;
        }

        @Override
        public void add(ChatMessage message) {
            if (failure == SavingFailure.ADDING_THE_SECOND_RESULT
                    && message instanceof ToolExecutionResultMessage
                    && hasToolResult()) {
                throw new IllegalStateException("store is down");
            }
            super.add(message);
        }

        @Override
        public List<ChatMessage> messages() {
            if (failure == SavingFailure.READING_THE_SAVED_RESULTS && hasToolResult() && !rolledBack.getAsBoolean()) {
                throw new IllegalStateException("store is down");
            }
            return super.messages();
        }

        private boolean hasToolResult() {
            return super.messages().stream().anyMatch(message -> message instanceof ToolExecutionResultMessage);
        }
    }

    // saves the tool results only once it is released, and then cannot be read until the tools are rolled back
    static class MemorySavingToolResultsOnceReleasedAndFailingToReadThem implements ChatMemory {

        private final ChatMemory delegate = MessageWindowChatMemory.withMaxMessages(20);
        private final BooleanSupplier rolledBack;
        final CountDownLatch savingToolResults = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        MemorySavingToolResultsOnceReleasedAndFailingToReadThem(BooleanSupplier rolledBack) {
            this.rolledBack = rolledBack;
        }

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
            if (messages.stream().noneMatch(message -> message instanceof ToolExecutionResultMessage)) {
                return delegate.addAsync(messages);
            }
            savingToolResults.countDown();
            return CompletableFuture.runAsync(() -> {
                        try {
                            release.await(10, SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    })
                    .thenCompose(ignored -> delegate.addAsync(messages));
        }

        @Override
        public CompletableFuture<List<ChatMessage>> messagesAsync() {
            boolean hasToolResult =
                    delegate.messages().stream().anyMatch(message -> message instanceof ToolExecutionResultMessage);
            if (hasToolResult && !rolledBack.getAsBoolean()) {
                return CompletableFuture.failedFuture(new IllegalStateException("store is down"));
            }
            return delegate.messagesAsync();
        }

        @Override
        public CompletableFuture<Void> setAsync(List<ChatMessage> messages) {
            return delegate.setAsync(messages);
        }
    }

    enum CancellationWhileSaving {
        WHILE_THE_FIRST_RESULT_IS_ADDED,
        ONCE_THE_RESULTS_ARE_ADDED
    }

    // cancels the invocation while the results of a round are saved: when the first tool result is added, or when the
    // messages are read once the results are added
    static class MemoryCancellingWhileSaving extends SyncOnlyMemory {

        private final CancellationWhileSaving moment;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        volatile StreamingHandle streamingHandle;

        MemoryCancellingWhileSaving(CancellationWhileSaving moment) {
            this.moment = moment;
        }

        @Override
        public void add(ChatMessage message) {
            if (moment == CancellationWhileSaving.WHILE_THE_FIRST_RESULT_IS_ADDED
                    && message instanceof ToolExecutionResultMessage) {
                cancelOnce();
            }
            super.add(message);
        }

        @Override
        public List<ChatMessage> messages() {
            List<ChatMessage> messages = super.messages();
            if (moment == CancellationWhileSaving.ONCE_THE_RESULTS_ARE_ADDED
                    && messages.stream().anyMatch(message -> message instanceof ToolExecutionResultMessage)) {
                cancelOnce();
            }
            return messages;
        }

        private void cancelOnce() {
            if (cancelled.compareAndSet(false, true)) {
                streamingHandle.cancel();
            }
        }
    }

    // a store that cannot save tool results
    static class MemoryFailingToSaveToolResults extends SyncOnlyMemory {

        final IllegalStateException failure = new IllegalStateException("store is down");

        @Override
        public void add(ChatMessage message) {
            if (message instanceof ToolExecutionResultMessage) {
                throw failure;
            }
            super.add(message);
        }
    }

    // saves the tool results only once it is released, and then fails to save them
    static class MemoryFailingToSaveToolResultsOnceReleased implements ChatMemory {

        private final ChatMemory delegate = MessageWindowChatMemory.withMaxMessages(20);
        final CountDownLatch savingToolResults = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

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
            if (messages.stream().noneMatch(message -> message instanceof ToolExecutionResultMessage)) {
                return delegate.addAsync(messages);
            }
            savingToolResults.countDown();
            return CompletableFuture.runAsync(() -> {
                try {
                    release.await(10, SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("store is down");
            });
        }

        @Override
        public CompletableFuture<List<ChatMessage>> messagesAsync() {
            return delegate.messagesAsync();
        }

        @Override
        public CompletableFuture<Void> setAsync(List<ChatMessage> messages) {
            return delegate.setAsync(messages);
        }
    }

    // runs the first tasks on the calling thread, and rejects the others
    static class ExecutorRejectingAfter implements Executor {

        private final AtomicInteger accepted;

        ExecutorRejectingAfter(int accepted) {
            this.accepted = new AtomicInteger(accepted);
        }

        @Override
        public void execute(Runnable task) {
            if (accepted.getAndDecrement() <= 0) {
                throw new RejectedExecutionException("no more tasks");
            }
            task.run();
        }
    }

    // runs the tasks on the calling thread, and rejects only the given one (the first one is 1)
    static class ExecutorRejectingOnly implements Executor {

        private final int rejectedTask;
        private final AtomicInteger tasks = new AtomicInteger();

        ExecutorRejectingOnly(int rejectedTask) {
            this.rejectedTask = rejectedTask;
        }

        @Override
        public void execute(Runnable task) {
            if (tasks.incrementAndGet() == rejectedTask) {
                throw new RejectedExecutionException("no more tasks");
            }
            task.run();
        }
    }

    // runs the first tasks late on another thread, and rejects the others
    static class LateExecutorRejectingAfter implements Executor {

        private final AtomicInteger accepted;

        LateExecutorRejectingAfter(int accepted) {
            this.accepted = new AtomicInteger(accepted);
        }

        @Override
        public void execute(Runnable task) {
            if (accepted.getAndDecrement() <= 0) {
                throw new RejectedExecutionException("no more tasks");
            }
            new Thread(() -> {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        task.run();
                    })
                    .start();
        }
    }

    enum Mode {
        SYNC,
        TOKEN_STREAM,
        COMPLETABLE_FUTURE,
        REACTIVE
    }

    enum ErrorHandling {
        SEND_TO_LLM,
        FAIL_INVOCATION
    }

    interface SyncAssistant {

        String chat(String message);
    }

    interface TokenStreamAssistant {

        TokenStream chat(String message);
    }

    interface AsyncAssistant {

        CompletableFuture<String> chat(String message);
    }

    interface ReactiveAssistant {

        Flow.Publisher<String> chat(String message);
    }

    static Stream<Arguments> modesAndToolConcurrency() {
        return Stream.of(Mode.values())
                .flatMap(mode -> Stream.of(Arguments.of(mode, false), Arguments.of(mode, true)));
    }

    static Stream<Arguments> modesAndErrorHandling() {
        return Stream.of(Mode.values())
                .flatMap(mode -> Stream.of(ErrorHandling.values()).map(handling -> Arguments.of(mode, handling)));
    }

    static Stream<Arguments> modesErrorHandlingAndToolCallIds() {
        return modesAndErrorHandling()
                .flatMap(arguments -> Stream.of("1", null)
                        .map(id -> Arguments.of(arguments.get()[0], arguments.get()[1], id)));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_roll_back_tools_of_earlier_rounds_before_a_rethrown_tool_error_fails_the_invocation(Mode mode) {

        Bank bank = new Bank();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(CREDIT), AiMessage.from(WITHDRAW), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .chatMemory(memory)
                        .compensateOnToolErrors(true)
                        .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                        .registerListener((ToolCompensatedEventListener) compensatedEvents::add));

        assertThat(answer)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isSameAs(bank.thrown);
        assertThat(bank.calls).containsExactly("credit:Mario", "withdraw:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> {
            assertThat(event.request().name()).isEqualTo("credit");
            assertThat(event.resultText()).isEqualTo("credited");
            assertThat(event.reason()).isEqualTo(CompensationReason.TOOL_EXECUTION_FAILED);
        });
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLED_BACK);
    }

    @ParameterizedTest
    @MethodSource("modesAndToolConcurrency")
    void should_roll_back_tools_of_the_same_round_before_a_rethrown_tool_error_fails_the_invocation(
            Mode mode, boolean executeToolsConcurrently) {

        Bank bank = new Bank();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();

        CompletableFuture<String> answer = chat(
                mode, List.of(AiMessage.from(CREDIT, WITHDRAW), AiMessage.from("done")), aiServices -> {
                    aiServices
                            .tools(bank)
                            .compensateOnToolErrors(true)
                            .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                            .registerListener((ToolCompensatedEventListener) compensatedEvents::add);
                    if (executeToolsConcurrently) {
                        aiServices.executeToolsConcurrently();
                    }
                });

        assertThat(answer)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isSameAs(bank.thrown);
        assertThat(bank.calls).containsExactlyInAnyOrder("credit:Mario", "withdraw:Mario", "uncredit:Mario");
        assertThat(bank.calls).last().isEqualTo("uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> {
            assertThat(event.request().name()).isEqualTo("credit");
            assertThat(event.reason()).isEqualTo(CompensationReason.TOOL_EXECUTION_FAILED);
        });
    }

    @ParameterizedTest
    @EnumSource(
            value = Mode.class,
            names = {"SYNC", "TOKEN_STREAM"})
    void should_roll_back_before_a_tool_arguments_error_fails_the_invocation_by_default(Mode mode) {

        // in these modes, an error while parsing the tool arguments fails the invocation by default
        Bank bank = new Bank();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);

        CompletableFuture<String> answer = chat(
                mode,
                List.of(
                        AiMessage.from(CREDIT),
                        AiMessage.from(WITHDRAW_WITH_INVALID_ARGUMENTS),
                        AiMessage.from("done")),
                aiServices -> aiServices.tools(bank).chatMemory(memory).compensateOnToolErrors(true));

        assertInvalidArgumentsFailure(answer);
        assertThat(bank.calls).containsExactly("credit:Mario", "uncredit:Mario");
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLED_BACK);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_roll_back_before_a_tool_arguments_error_fails_the_invocation(Mode mode) {

        Bank bank = new Bank();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);

        CompletableFuture<String> answer = chat(
                mode,
                List.of(
                        AiMessage.from(CREDIT),
                        AiMessage.from(WITHDRAW_WITH_INVALID_ARGUMENTS),
                        AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .chatMemory(memory)
                        .compensateOnToolErrors(true)
                        .toolArgumentsErrorHandler(ToolArgumentsErrorHandler.failInvocation()));

        assertInvalidArgumentsFailure(answer);
        assertThat(bank.calls).containsExactly("credit:Mario", "uncredit:Mario");
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLED_BACK);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_roll_back_when_a_tool_error_is_sent_to_the_llm(Mode mode) {

        Bank bank = new Bank();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(CREDIT), AiMessage.from(WITHDRAW), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .chatMemory(memory)
                        .compensateOnToolErrors(true)
                        .toolExecutionErrorHandler(ToolExecutionErrorHandler.sendExceptionMessageToLlm())
                        .registerListener((ToolCompensatedEventListener) compensatedEvents::add));

        assertThat(answer).succeedsWithin(10, SECONDS).isEqualTo("done");
        assertThat(bank.calls).containsExactly("credit:Mario", "withdraw:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> {
            assertThat(event.request().name()).isEqualTo("credit");
            assertThat(event.reason()).isEqualTo(CompensationReason.TOOL_EXECUTION_FAILED);
        });
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLED_BACK);
        assertThat(toolResultTexts(memory, "withdraw")).containsExactly("insufficient funds");
    }

    @ParameterizedTest
    @MethodSource("modesErrorHandlingAndToolCallIds")
    void should_rewrite_the_result_of_this_invocation_when_tool_call_ids_are_repeated_or_missing(
            Mode mode, ErrorHandling errorHandling, String toolCallId) throws Exception {

        // an earlier turn of the conversation has the same tool call, with the same id or without an id
        ToolExecutionRequest credit = CREDIT.toBuilder().id(toolCallId).build();
        ToolExecutionRequest withdraw = WITHDRAW.toBuilder().id(toolCallId == null ? null : "2").build();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        memory.add(UserMessage.from("transfer"));
        memory.add(AiMessage.from(credit));
        memory.add(ToolExecutionResultMessage.builder()
                .id(toolCallId)
                .toolName("credit")
                .text("credited")
                .build());
        memory.add(AiMessage.from("transferred"));
        Bank bank = new Bank();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(credit), AiMessage.from(withdraw), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .chatMemory(memory)
                        .compensateOnToolErrors(true)
                        .toolExecutionErrorHandler(
                                errorHandling == ErrorHandling.SEND_TO_LLM
                                        ? ToolExecutionErrorHandler.sendExceptionMessageToLlm()
                                        : ToolExecutionErrorHandler.failInvocation()));

        if (errorHandling == ErrorHandling.SEND_TO_LLM) {
            assertThat(answer).succeedsWithin(10, SECONDS).isEqualTo("done");
        } else {
            assertThat(failureOf(answer)).isSameAs(bank.thrown);
            assertThat(bank.thrown.getSuppressed()).isEmpty();
        }
        assertThat(bank.calls).containsExactly("credit:Mario", "withdraw:Mario", "uncredit:Mario");
        assertThat(toolResultTexts(memory, "credit")).containsExactly("credited", CREDIT_ROLLED_BACK);
    }

    @ParameterizedTest
    @MethodSource("modesErrorHandlingAndToolCallIds")
    void should_not_rewrite_the_result_of_an_earlier_turn_when_a_tool_of_the_failing_round_is_rolled_back(
            Mode mode, ErrorHandling errorHandling, String toolCallId) throws Exception {

        // an earlier turn of the conversation has the same tool call and result, with the same id or without an id
        ToolExecutionRequest credit = CREDIT.toBuilder().id(toolCallId).build();
        ToolExecutionRequest withdraw = WITHDRAW.toBuilder().id(toolCallId == null ? null : "2").build();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        memory.add(UserMessage.from("transfer"));
        memory.add(AiMessage.from(credit));
        memory.add(ToolExecutionResultMessage.builder()
                .id(toolCallId)
                .toolName("credit")
                .text("credited")
                .isError(false)
                .build());
        memory.add(AiMessage.from("transferred"));
        Bank bank = new Bank();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(credit, withdraw), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .chatMemory(memory)
                        .compensateOnToolErrors(true)
                        .toolExecutionErrorHandler(
                                errorHandling == ErrorHandling.SEND_TO_LLM
                                        ? ToolExecutionErrorHandler.sendExceptionMessageToLlm()
                                        : ToolExecutionErrorHandler.failInvocation()));

        if (errorHandling == ErrorHandling.SEND_TO_LLM) {
            assertThat(answer).succeedsWithin(10, SECONDS).isEqualTo("done");
            assertThat(toolResultTexts(memory, "credit")).containsExactly("credited", CREDIT_ROLLED_BACK);
        } else {
            assertThat(failureOf(answer)).isSameAs(bank.thrown);
            // the results of the failing round are not saved
            assertThat(toolResultTexts(memory, "credit")).containsExactly("credited");
        }
        assertThat(bank.calls).containsExactlyInAnyOrder("credit:Mario", "withdraw:Mario", "uncredit:Mario");
    }

    @ParameterizedTest
    @MethodSource("modesAndErrorHandling")
    void should_rewrite_the_result_of_each_rolled_back_tool_when_a_tool_call_id_repeats_in_the_invocation(
            Mode mode, ErrorHandling errorHandling) throws Exception {

        // some providers number the tool calls of each response from 0
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        Bank bank = new Bank();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(
                        AiMessage.from(RESERVE.toBuilder().id("0").build()),
                        AiMessage.from(
                                CREDIT.toBuilder().id("0").build(),
                                WITHDRAW.toBuilder().id("1").build()),
                        AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .chatMemory(memory)
                        .compensateOnToolErrors(true)
                        .toolExecutionErrorHandler(
                                errorHandling == ErrorHandling.SEND_TO_LLM
                                        ? ToolExecutionErrorHandler.sendExceptionMessageToLlm()
                                        : ToolExecutionErrorHandler.failInvocation()));

        if (errorHandling == ErrorHandling.SEND_TO_LLM) {
            assertThat(answer).succeedsWithin(10, SECONDS).isEqualTo("done");
            assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLED_BACK);
        } else {
            assertThat(failureOf(answer)).isSameAs(bank.thrown);
            // the results of the failing round are not saved
            assertThat(toolResultTexts(memory, "credit")).isEmpty();
        }
        assertThat(toolResultTexts(memory, "reserve")).containsExactly(RESERVE_ROLLED_BACK);
        assertThat(bank.calls)
                .containsExactlyInAnyOrder(
                        "reserve:Mario", "credit:Mario", "withdraw:Mario", "uncredit:Mario", "unreserve:Mario");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_give_each_rolled_back_tool_its_own_result_when_tool_calls_have_no_id(Mode mode) {

        // two identical calls without an id; the rollback of the second one runs first, and fails
        ToolExecutionRequest credit = CREDIT.toBuilder().id(null).build();
        ToolExecutionRequest withdraw = WITHDRAW.toBuilder().id(null).build();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        BankWithFirstUncreditFailing bank = new BankWithFirstUncreditFailing();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(
                        AiMessage.from(credit),
                        AiMessage.from(credit),
                        AiMessage.from(withdraw),
                        AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .chatMemory(memory)
                        .compensateOnToolErrors(true)
                        .toolExecutionErrorHandler(ToolExecutionErrorHandler.sendExceptionMessageToLlm()));

        assertThat(answer).succeedsWithin(10, SECONDS).isEqualTo("done");
        assertThat(bank.calls)
                .containsExactly(
                        "credit:Mario", "credit:Mario", "withdraw:Mario", "uncredit:Mario", "uncredit:Mario");
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLED_BACK, CREDIT_ROLLBACK_FAILED);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_add_a_failure_while_rolling_back_as_suppressed_to_the_rethrown_tool_error(Mode mode) {

        Bank bank = new Bank();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(CREDIT), AiMessage.from(WITHDRAW), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .chatMemory(new MemoryFailingOnRewrite())
                        .compensateOnToolErrors(true)
                        .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                        .registerListener((ToolCompensatedEventListener) compensatedEvents::add));

        assertThat(answer)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isSameAs(bank.thrown);
        assertThat(bank.thrown.getSuppressed()).singleElement().satisfies(suppressed -> assertThat(suppressed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("store is down"));
        assertThat(bank.calls).containsExactly("credit:Mario", "withdraw:Mario", "uncredit:Mario");
        // credit was rolled back, although its result in the chat memory could not be rewritten
        assertThat(compensatedEvents).singleElement().satisfies(event -> {
            assertThat(event.request().name()).isEqualTo("credit");
            assertThat(event.reason()).isEqualTo(CompensationReason.TOOL_EXECUTION_FAILED);
            assertThat(event.compensationError()).isNull();
        });
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_roll_back_before_failing_when_the_tool_executor_rejects_a_tool(Mode mode) {

        // the sync mode runs a single tool call itself, and only the tool calls of a round with several on the executor
        Executor executor = new ExecutorRejectingAfter(mode == Mode.SYNC ? 1 : 2);
        Bank bank = new Bank();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(CREDIT), AiMessage.from(SECOND_CREDIT, WITHDRAW), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .executeToolsConcurrently(executor)
                        .compensateOnToolErrors(true)
                        .registerListener((ToolCompensatedEventListener) compensatedEvents::add));

        assertThat(answer)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isInstanceOf(RejectedExecutionException.class);
        assertThat(bank.calls).containsExactly("credit:Mario", "credit:Mario", "uncredit:Mario", "uncredit:Mario");
        assertThat(compensatedEvents)
                .extracting(event -> event.request().id())
                .containsExactlyInAnyOrder("1", "3");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_wait_for_the_started_tools_when_the_tool_executor_rejects_a_tool_without_compensation(Mode mode) {

        // the sync mode runs a single tool call itself, and only the tool calls of a round with several on the executor
        Executor executor = new LateExecutorRejectingAfter(mode == Mode.SYNC ? 1 : 2);
        Bank bank = new Bank();
        List<ToolExecutedEvent> executedEvents = new CopyOnWriteArrayList<>();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(CREDIT), AiMessage.from(SECOND_CREDIT, WITHDRAW), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .executeToolsConcurrently(executor)
                        .registerListener((ToolExecutedEventListener) executedEvents::add));

        assertThat(answer)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isInstanceOf(RejectedExecutionException.class);
        // the second credit, started before withdraw was rejected, has finished before the invocation failed
        assertThat(bank.calls).containsExactly("credit:Mario", "credit:Mario");
        assertThat(executedEvents)
                .extracting(event -> event.request().id(), ToolExecutedEvent::isError)
                .containsExactlyInAnyOrder(tuple("1", false), tuple("3", false), tuple("2", true));
    }

    static Stream<Arguments> modesWithAndWithoutCompensation() {
        return Stream.of(Mode.values())
                .flatMap(mode -> Stream.of(Arguments.of(mode, false), Arguments.of(mode, true)));
    }

    @ParameterizedTest
    @MethodSource("modesWithAndWithoutCompensation")
    void should_still_run_the_later_tool_calls_of_the_response_when_the_tool_executor_rejects_one(
            Mode mode, boolean compensateOnToolErrors) {

        // the second of the three tool calls of the response is rejected
        Executor executor = new ExecutorRejectingOnly(2);
        Bank bank = new Bank();
        List<ToolExecutedEvent> executedEvents = new CopyOnWriteArrayList<>();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(CREDIT, SECOND_CREDIT, CREDIT_FOR_LUIGI), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .executeToolsConcurrently(executor)
                        .compensateOnToolErrors(compensateOnToolErrors)
                        .registerListener((ToolExecutedEventListener) executedEvents::add)
                        .registerListener((ToolCompensatedEventListener) compensatedEvents::add));

        assertThat(answer)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isInstanceOf(RejectedExecutionException.class);
        assertThat(executedEvents)
                .extracting(event -> event.request().id(), ToolExecutedEvent::isError)
                .containsExactlyInAnyOrder(tuple("1", false), tuple("3", true), tuple("6", false));
        if (compensateOnToolErrors) {
            assertThat(bank.calls)
                    .containsExactly("credit:Mario", "credit:Luigi", "uncredit:Luigi", "uncredit:Mario");
            assertThat(compensatedEvents)
                    .extracting(event -> event.request().id())
                    .containsExactlyInAnyOrder("1", "6");
        } else {
            assertThat(bank.calls).containsExactly("credit:Mario", "credit:Luigi");
            assertThat(compensatedEvents).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_say_in_the_chat_memory_that_rolling_back_a_tool_failed(Mode mode) {

        BankWithFailingUncredit bank = new BankWithFailingUncredit();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(RESERVE), AiMessage.from(CREDIT, WITHDRAW), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .chatMemory(memory)
                        .compensateOnToolErrors(true)
                        .toolExecutionErrorHandler(ToolExecutionErrorHandler.sendExceptionMessageToLlm())
                        .registerListener((ToolCompensatedEventListener) compensatedEvents::add));

        assertThat(answer).succeedsWithin(10, SECONDS).isEqualTo("done");
        // the failing rollback of credit does not stop the rollback of reserve
        assertCallsOfFailedRollback(bank);
        assertCompensationErrorsOfFailedRollback(compensatedEvents, bank);
        // credit is a result of the round that failed, reserve one of an earlier round
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLBACK_FAILED);
        assertThat(toolResultTexts(memory, "reserve")).containsExactly(RESERVE_ROLLED_BACK);
        assertThat(toolResultTexts(memory, "withdraw")).containsExactly("insufficient funds");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_add_the_error_of_a_failed_rollback_as_suppressed_to_the_tool_error_that_fails_the_invocation(
            Mode mode) throws Exception {

        BankWithFailingUncredit bank = new BankWithFailingUncredit();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(RESERVE), AiMessage.from(CREDIT, WITHDRAW), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .chatMemory(memory)
                        .compensateOnToolErrors(true)
                        .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                        .registerListener((ToolCompensatedEventListener) compensatedEvents::add));

        Throwable failure = failureOf(answer);
        assertThat(failure).isSameAs(bank.thrown);
        assertThat(failure.getSuppressed()).singleElement().isSameAs(bank.uncreditFailure);
        assertCallsOfFailedRollback(bank);
        assertCompensationErrorsOfFailedRollback(compensatedEvents, bank);
        assertThat(toolResultTexts(memory, "reserve")).containsExactly(RESERVE_ROLLED_BACK);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_add_the_error_of_a_failed_rollback_as_suppressed_also_when_rewriting_the_chat_memory_fails(
            Mode mode) throws Exception {

        BankWithFailingUncredit bank = new BankWithFailingUncredit();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(RESERVE), AiMessage.from(CREDIT, WITHDRAW), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .chatMemory(new MemoryFailingOnRewrite())
                        .compensateOnToolErrors(true)
                        .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                        .registerListener((ToolCompensatedEventListener) compensatedEvents::add));

        Throwable failure = failureOf(answer);
        assertThat(failure).isSameAs(bank.thrown);
        assertThat(failure.getSuppressed()).hasSize(2);
        assertThat(failure.getSuppressed()[0]).isSameAs(bank.uncreditFailure);
        assertThat(failure.getSuppressed()[1])
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("store is down");
        assertCallsOfFailedRollback(bank);
        // the events are fired although the chat memory could not be rewritten
        assertCompensationErrorsOfFailedRollback(compensatedEvents, bank);
    }

    @ParameterizedTest
    @EnumSource(
            value = Mode.class,
            names = {"COMPLETABLE_FUTURE", "REACTIVE"})
    void should_fire_the_compensated_events_after_a_cancellation_also_when_rewriting_the_chat_memory_fails(Mode mode)
            throws Exception {

        BankWithFailingUncreditAndSlowReserve bank = new BankWithFailingUncreditAndSlowReserve();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        CountDownLatch rolledBack = new CountDownLatch(2);
        Consumer<AiServices<?>> configurer = aiServices -> aiServices
                .tools(bank)
                .chatMemory(new MemoryFailingOnRewrite())
                .executeToolsConcurrently()
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) event -> {
                    compensatedEvents.add(event);
                    rolledBack.countDown();
                });
        Runnable cancel = startCancellable(
                mode, List.of(AiMessage.from(CREDIT), AiMessage.from(RESERVE), AiMessage.from("done")), configurer);
        assertThat(bank.reserveStarted.await(10, SECONDS)).isTrue();
        cancel.run();
        bank.releaseReserve.countDown();

        // the result of credit, saved in the first round, cannot be rewritten
        assertThat(rolledBack.await(10, SECONDS)).isTrue();
        assertThat(bank.calls)
                .containsExactly("credit:Mario", "reserve:Mario", "unreserve:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).allSatisfy(event -> {
            assertThat(event.reason()).isEqualTo(CompensationReason.INVOCATION_CANCELLED);
            if (event.request().name().equals("credit")) {
                assertThat(event.compensationError()).isSameAs(bank.uncreditFailure);
            } else {
                assertThat(event.compensationError()).isNull();
            }
        });
    }

    static Stream<Arguments> modesAndFailingCompensatingActions() {
        return Stream.of(Mode.values())
                .flatMap(mode -> Stream.of(
                        Arguments.of(mode, new BankWithFailingUncreditTakingTheToolExecution()),
                        Arguments.of(mode, new BankWithUncreditReturningAFailedFuture()),
                        Arguments.of(mode, new BankWithUncreditTakingTheToolExecutionReturningAFailedFutureBox()),
                        Arguments.of(mode, new BankWithUncreditTakingTheToolExecutionFailingWithAToolExecutionException())));
    }

    @ParameterizedTest
    @MethodSource("modesAndFailingCompensatingActions")
    void should_report_the_error_the_compensating_action_failed_with(Mode mode, Object bank) throws Exception {

        Throwable uncreditFailure;
        if (bank instanceof BankWithFailingUncreditTakingTheToolExecution taking) {
            uncreditFailure = taking.uncreditFailure;
        } else if (bank instanceof BankWithUncreditReturningAFailedFuture returning) {
            uncreditFailure = returning.uncreditFailure;
        } else if (bank instanceof BankWithUncreditTakingTheToolExecutionFailingWithAToolExecutionException failing) {
            uncreditFailure = failing.uncreditFailure;
        } else {
            uncreditFailure = ((BankWithUncreditTakingTheToolExecutionReturningAFailedFutureBox) bank).uncreditFailure;
        }
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(CREDIT), AiMessage.from(WITHDRAW), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .compensateOnToolErrors(true)
                        .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                        .registerListener((ToolCompensatedEventListener) compensatedEvents::add));

        Throwable failure = failureOf(answer);
        assertThat(failure).isInstanceOf(InsufficientFundsException.class);
        assertThat(failure.getSuppressed()).singleElement().isSameAs(uncreditFailure);
        assertThat(compensatedEvents)
                .singleElement()
                .satisfies(event -> assertThat(event.compensationError()).isSameAs(uncreditFailure));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_wait_for_a_compensating_action_that_takes_the_tool_execution_and_returns_an_adapted_type(Mode mode) {

        BankWithLateUncreditTakingTheToolExecution bank = new BankWithLateUncreditTakingTheToolExecution();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(CREDIT), AiMessage.from(WITHDRAW), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .compensateOnToolErrors(true)
                        .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                        .registerListener((ToolCompensatedEventListener) compensatedEvents::add));

        assertThat(answer)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isInstanceOf(InsufficientFundsException.class);
        // the rollback has finished before the invocation failed
        assertThat(bank.calls).containsExactly("credit:Mario", "withdraw:Mario", "uncredit:1");
        assertThat(compensatedEvents)
                .singleElement()
                .satisfies(event -> assertThat(event.compensationError()).isNull());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_roll_back_before_failing_when_the_llm_calls_a_tool_that_does_not_exist(Mode mode) {

        // by default, a call to a tool that does not exist fails the invocation
        ToolExecutionRequest unknownTool = ToolExecutionRequest.builder()
                .id("5")
                .name("transfer")
                .arguments("{}")
                .build();
        Bank bank = new Bank();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(CREDIT), AiMessage.from(unknownTool), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .compensateOnToolErrors(true)
                        .registerListener((ToolCompensatedEventListener) compensatedEvents::add));

        assertThat(answer)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .withMessageContaining("transfer");
        assertThat(bank.calls).containsExactly("credit:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> assertThat(event.request().name())
                .isEqualTo("credit"));
    }

    @ParameterizedTest
    @EnumSource(
            value = Mode.class,
            names = {"COMPLETABLE_FUTURE", "REACTIVE"})
    void should_say_in_the_chat_memory_that_rolling_back_a_tool_failed_after_a_cancellation(Mode mode)
            throws Exception {

        BankWithFailingUncreditAndSlowReserve bank = new BankWithFailingUncreditAndSlowReserve();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        CountDownLatch rolledBack = new CountDownLatch(2);
        Consumer<AiServices<?>> configurer = aiServices -> aiServices
                .tools(bank)
                .chatMemory(memory)
                .executeToolsConcurrently()
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) event -> {
                    compensatedEvents.add(event);
                    rolledBack.countDown();
                });
        Runnable cancel = startCancellable(
                mode, List.of(AiMessage.from(CREDIT), AiMessage.from(RESERVE), AiMessage.from("done")), configurer);
        assertThat(bank.reserveStarted.await(10, SECONDS)).isTrue();
        cancel.run();
        bank.releaseReserve.countDown();

        assertThat(rolledBack.await(10, SECONDS)).isTrue();
        assertThat(bank.calls)
                .containsExactly("credit:Mario", "reserve:Mario", "unreserve:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).allSatisfy(event -> {
            assertThat(event.reason()).isEqualTo(CompensationReason.INVOCATION_CANCELLED);
            if (event.request().name().equals("credit")) {
                assertThat(event.compensationError()).isSameAs(bank.uncreditFailure);
            } else {
                assertThat(event.compensationError()).isNull();
            }
        });
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLBACK_FAILED_ON_CANCELLATION);
        // the results of the cancelled round are not saved
        assertThat(toolResultTexts(memory, "reserve")).isEmpty();
    }

    static Stream<Arguments> nonBlockingModesAndToolCallIds() {
        return Stream.of(Mode.COMPLETABLE_FUTURE, Mode.REACTIVE)
                .flatMap(mode -> Stream.of("1", null).map(id -> Arguments.of(mode, id)));
    }

    @ParameterizedTest
    @MethodSource("nonBlockingModesAndToolCallIds")
    void should_not_rewrite_the_result_of_an_earlier_turn_when_a_tool_is_rolled_back_after_a_cancellation(
            Mode mode, String toolCallId) throws Exception {

        // an earlier turn of the conversation has the same tool call and result, with the same id or without an id
        ToolExecutionRequest credit = CREDIT.toBuilder().id(toolCallId).build();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        memory.add(UserMessage.from("transfer"));
        memory.add(AiMessage.from(credit));
        memory.add(ToolExecutionResultMessage.builder()
                .id(toolCallId)
                .toolName("credit")
                .text("credited")
                .isError(false)
                .build());
        memory.add(AiMessage.from("transferred"));
        SlowBank bank = new SlowBank();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        CountDownLatch rolledBack = new CountDownLatch(1);
        Consumer<AiServices<?>> configurer = aiServices -> aiServices
                .tools(bank)
                .chatMemory(memory)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) event -> {
                    compensatedEvents.add(event);
                    rolledBack.countDown();
                });

        Runnable cancel = startCancellable(mode, List.of(AiMessage.from(credit), AiMessage.from("done")), configurer);
        assertThat(bank.started.await(10, SECONDS)).isTrue();
        cancel.run();
        bank.release.countDown();

        assertThat(rolledBack.await(10, SECONDS)).isTrue();
        assertThat(bank.calls).containsExactly("credit:Mario", "credit finished", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> assertThat(event.reason())
                .isEqualTo(CompensationReason.INVOCATION_CANCELLED));
        // the result of the cancelled round was not saved, and the result of the earlier turn is left as it was
        assertThat(toolResultTexts(memory, "credit")).containsExactly("credited");
    }

    static Stream<String> toolCallIds() {
        return Stream.of("1", null);
    }

    @ParameterizedTest
    @MethodSource("toolCallIds")
    void flow_publisher_should_not_rewrite_the_result_of_an_earlier_turn_when_cancelled_while_results_fail_to_be_saved(
            String toolCallId) throws Exception {

        // an earlier turn of the conversation has the same tool call and result, with the same id or without an id
        ToolExecutionRequest credit = CREDIT.toBuilder().id(toolCallId).build();
        MemoryFailingToSaveToolResultsOnceReleased memory = new MemoryFailingToSaveToolResultsOnceReleased();
        memory.add(UserMessage.from("transfer"));
        memory.add(AiMessage.from(credit));
        memory.add(ToolExecutionResultMessage.builder()
                .id(toolCallId)
                .toolName("credit")
                .text("credited")
                .isError(false)
                .build());
        memory.add(AiMessage.from("transferred"));
        Bank bank = new Bank();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        CountDownLatch rolledBack = new CountDownLatch(1);
        Consumer<AiServices<?>> configurer = aiServices -> aiServices
                .tools(bank)
                .chatMemory(memory)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) event -> {
                    compensatedEvents.add(event);
                    rolledBack.countDown();
                });

        // the round is complete and its results are being saved when the invocation is cancelled
        Runnable cancel = startCancellable(
                Mode.REACTIVE, List.of(AiMessage.from(credit), AiMessage.from("done")), configurer);
        assertThat(memory.savingToolResults.await(10, SECONDS)).isTrue();
        cancel.run();
        memory.release.countDown();

        assertThat(rolledBack.await(10, SECONDS)).isTrue();
        assertThat(bank.calls).containsExactly("credit:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> assertThat(event.reason())
                .isEqualTo(CompensationReason.INVOCATION_CANCELLED));
        // the results of the round could not be saved, and the result of the earlier turn is left as it was
        assertThat(toolResultTexts(memory, "credit")).containsExactly("credited");
    }

    @Test
    void flow_publisher_should_rewrite_the_saved_results_when_cancelled_while_saving_them_and_reading_them_back_fails()
            throws Exception {

        Bank bank = new Bank();
        MemorySavingToolResultsOnceReleasedAndFailingToReadThem memory =
                new MemorySavingToolResultsOnceReleasedAndFailingToReadThem(
                        () -> bank.calls.contains("uncredit:Mario"));
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        CountDownLatch rolledBack = new CountDownLatch(1);
        Consumer<AiServices<?>> configurer = aiServices -> aiServices
                .tools(bank)
                .chatMemory(memory)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) event -> {
                    compensatedEvents.add(event);
                    rolledBack.countDown();
                });

        // the round is complete and its results are being saved when the invocation is cancelled; they are saved, and
        // then reading the messages back fails
        Runnable cancel = startCancellable(
                Mode.REACTIVE, List.of(AiMessage.from(CREDIT), AiMessage.from("done")), configurer);
        assertThat(memory.savingToolResults.await(10, SECONDS)).isTrue();
        cancel.run();
        memory.release.countDown();

        assertThat(rolledBack.await(10, SECONDS)).isTrue();
        assertThat(bank.calls).containsExactly("credit:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> assertThat(event.reason())
                .isEqualTo(CompensationReason.INVOCATION_CANCELLED));
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLED_BACK_ON_CANCELLATION);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void token_stream_should_report_to_cross_agent_compensation_before_a_rethrown_tool_error_fails_the_invocation(
            boolean executeToolsConcurrently) {

        Bank bank = new Bank();
        List<String> reported = new CopyOnWriteArrayList<>();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from(CREDIT, WITHDRAW_WITH_INVALID_ARGUMENTS), AiMessage.from("done"));
        CompletableFuture<Throwable> error = new CompletableFuture<>();

        tokenStream(model, bank, null, null, executeToolsConcurrently ? Runnable::run : null, context -> {
                    context.toolService.onCompensableToolExecution((toolExecution, compensatingAction) ->
                            reported.add("compensable:" + toolExecution.request().name()));
                    context.toolService.onToolExecutionError(invocationContext -> reported.add("tool error"));
                })
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(ignored -> error.complete(null))
                .onError(error::complete)
                .start();

        assertThat(error).succeedsWithin(10, SECONDS).isNotNull();
        assertThat(reported).containsExactly("compensable:credit", "tool error");
    }

    @Test
    void token_stream_should_say_in_the_chat_memory_that_rolling_back_a_tool_failed_after_a_cancellation() {

        BankWithFailingUncredit bank = new BankWithFailingUncredit();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from(CREDIT), AiMessage.from("Withdrawing", List.of(WITHDRAW)), AiMessage.from("done"));

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(model)
                .chatMemory(memory)
                .tools(bank)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add)
                .build();

        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        assistant
                .chat("transfer")
                .onPartialResponseWithContext((partialResponse, context) -> {
                    context.streamingHandle().cancel();
                    cancelled.complete(null);
                })
                .onError(ignored -> {})
                .start();

        assertThat(cancelled).succeedsWithin(10, SECONDS);
        assertThat(bank.calls).containsExactly("credit:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> {
            assertThat(event.reason()).isEqualTo(CompensationReason.INVOCATION_CANCELLED);
            assertThat(event.compensationError()).isSameAs(bank.uncreditFailure);
        });
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLBACK_FAILED_ON_CANCELLATION);
    }

    @ParameterizedTest
    @MethodSource("modesAndErrorHandling")
    void should_not_roll_back_when_compensation_is_not_enabled(Mode mode, ErrorHandling errorHandling) {

        Bank bank = new Bank();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();

        CompletableFuture<String> answer = chat(
                mode,
                List.of(AiMessage.from(CREDIT), AiMessage.from(WITHDRAW), AiMessage.from("done")),
                aiServices -> aiServices
                        .tools(bank)
                        .chatMemory(memory)
                        .toolExecutionErrorHandler(
                                errorHandling == ErrorHandling.SEND_TO_LLM
                                        ? ToolExecutionErrorHandler.sendExceptionMessageToLlm()
                                        : ToolExecutionErrorHandler.failInvocation())
                        .registerListener((ToolCompensatedEventListener) compensatedEvents::add));

        if (errorHandling == ErrorHandling.SEND_TO_LLM) {
            assertThat(answer).succeedsWithin(10, SECONDS).isEqualTo("done");
            assertThat(toolResultTexts(memory, "withdraw")).containsExactly("insufficient funds");
        } else {
            assertThat(answer)
                    .failsWithin(10, SECONDS)
                    .withThrowableOfType(ExecutionException.class)
                    .havingRootCause()
                    .isSameAs(bank.thrown);
        }
        assertThat(bank.calls).containsExactly("credit:Mario", "withdraw:Mario");
        assertThat(compensatedEvents).isEmpty();
        assertThat(toolResultTexts(memory, "credit")).containsExactly("credited");
    }

    @Test
    void token_stream_should_roll_back_when_cancelled_in_a_later_round() {

        Bank bank = new Bank();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from(CREDIT), AiMessage.from("Withdrawing", List.of(WITHDRAW)), AiMessage.from("done"));

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(model)
                .chatMemory(memory)
                .tools(bank)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add)
                .build();

        AtomicReference<Thread> cancellingThread = new AtomicReference<>();
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletableFuture<Object> completed = new CompletableFuture<>();
        assistant
                .chat("transfer")
                .onPartialResponseWithContext((partialResponse, context) -> {
                    cancellingThread.set(Thread.currentThread());
                    context.streamingHandle().cancel();
                    cancelled.complete(null);
                })
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start();

        assertThat(cancelled).succeedsWithin(10, SECONDS);
        assertThat(bank.calls).containsExactly("credit:Mario", "uncredit:Mario");
        // no tool was running, so the rollback ran on the thread that cancelled
        assertThat(bank.uncreditThread).isSameAs(cancellingThread.get());
        assertThat(compensatedEvents).singleElement().satisfies(event -> {
            assertThat(event.request().name()).isEqualTo("credit");
            assertThat(event.reason()).isEqualTo(CompensationReason.INVOCATION_CANCELLED);
        });
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLED_BACK_ON_CANCELLATION);
        assertThat(model.requests()).hasSize(2);
        assertThat(completed).isNotDone();
    }

    @Test
    void token_stream_should_roll_back_on_cancellation_with_a_chat_memory_that_only_has_blocking_methods() {

        Bank bank = new Bank();
        ChatMemory memory = new SyncOnlyMemory();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(MockStreamingChatModel.thatResponds(
                        AiMessage.from(CREDIT), AiMessage.from("Withdrawing", List.of(WITHDRAW))))
                .chatMemory(memory)
                .tools(bank)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add)
                .build();

        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        assistant
                .chat("transfer")
                .onPartialResponseWithContext((partialResponse, context) -> {
                    context.streamingHandle().cancel();
                    cancelled.complete(null);
                })
                .onError(ignored -> {})
                .start();

        assertThat(cancelled).succeedsWithin(10, SECONDS);
        assertThat(compensatedEvents).hasSize(1);
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLED_BACK_ON_CANCELLATION);
    }

    @Test
    void token_stream_should_roll_back_on_cancellation_even_if_a_listener_of_the_cancellation_throws() {

        Bank bank = new Bank();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from(CREDIT), AiMessage.from("Withdrawing", List.of(WITHDRAW)));

        TokenStream tokenStream = tokenStream(model, bank, null, context -> {
            context.eventListenerRegistrar.shouldThrowExceptionOnEventError(true);
            context.eventListenerRegistrar.register((AiServiceCancelledListener) event -> {
                throw new IllegalStateException("listener failed");
            });
            context.eventListenerRegistrar.register((ToolCompensatedEventListener) compensatedEvents::add);
        });

        CompletableFuture<Throwable> cancelFailure = new CompletableFuture<>();
        tokenStream
                .onPartialResponseWithContext((partialResponse, context) -> {
                    try {
                        context.streamingHandle().cancel();
                        cancelFailure.complete(null);
                    } catch (RuntimeException e) {
                        cancelFailure.complete(e);
                    }
                })
                .onError(ignored -> {})
                .start();

        assertThat(cancelFailure)
                .succeedsWithin(10, SECONDS)
                .satisfies(failure -> assertThat(failure)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("listener failed"));
        assertThat(bank.calls).containsExactly("credit:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).hasSize(1);
    }

    @Test
    void token_stream_should_roll_back_on_cancellation_on_the_complete_response_processing_executor() {

        Bank bank = new Bank();
        CompletableFuture<ToolCompensatedEvent> compensated = new CompletableFuture<>();
        List<Thread> processingThreads = new CopyOnWriteArrayList<>();
        Executor processingExecutor = task -> {
            Thread thread = new Thread(task, "processing");
            processingThreads.add(thread);
            thread.start();
        };
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from(CREDIT), AiMessage.from("Withdrawing", List.of(WITHDRAW)));

        TokenStream tokenStream = tokenStream(
                model,
                bank,
                processingExecutor,
                context -> context.eventListenerRegistrar.register(
                        (ToolCompensatedEventListener) compensated::complete));

        AtomicReference<Thread> cancellingThread = new AtomicReference<>();
        tokenStream
                .onPartialResponseWithContext((partialResponse, context) -> {
                    cancellingThread.set(Thread.currentThread());
                    context.streamingHandle().cancel();
                })
                .onError(ignored -> {})
                .start();

        assertThat(compensated)
                .succeedsWithin(10, SECONDS)
                .satisfies(event -> assertThat(event.reason()).isEqualTo(CompensationReason.INVOCATION_CANCELLED));
        assertThat(bank.calls).containsExactly("credit:Mario", "uncredit:Mario");
        assertThat(processingThreads).contains(bank.uncreditThread);
        assertThat(bank.uncreditThread).isNotSameAs(cancellingThread.get());
    }

    @Test
    void token_stream_should_not_deliver_the_final_answer_when_cancelled_before_it_is_processed() throws Exception {

        Bank bank = new Bank();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        CompletableFuture<ToolCompensatedEvent> compensated = new CompletableFuture<>();
        CountDownLatch finalAnswerHeld = new CountDownLatch(1);
        CountDownLatch releaseFinalAnswer = new CountDownLatch(1);
        AtomicInteger submittedTasks = new AtomicInteger();
        List<Thread> processingThreads = new CopyOnWriteArrayList<>();
        // the second task processes the final answer: it is held until the invocation is cancelled
        Executor processingExecutor = task -> {
            int taskNumber = submittedTasks.incrementAndGet();
            Thread thread = new Thread(() -> {
                if (taskNumber == 2) {
                    finalAnswerHeld.countDown();
                    try {
                        releaseFinalAnswer.await(10, SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                task.run();
            });
            processingThreads.add(thread);
            thread.start();
        };
        MockStreamingChatModel model =
                MockStreamingChatModel.thatResponds(AiMessage.from(CREDIT), AiMessage.from("done"));

        TokenStream tokenStream = tokenStream(model, bank, processingExecutor, memory, context -> context
                .eventListenerRegistrar.register((ToolCompensatedEventListener) compensated::complete));

        AtomicReference<StreamingHandle> streamingHandle = new AtomicReference<>();
        CompletableFuture<Object> completed = new CompletableFuture<>();
        tokenStream
                .onPartialResponseWithContext(
                        (partialResponse, context) -> streamingHandle.set(context.streamingHandle()))
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start();

        assertThat(finalAnswerHeld.await(10, SECONDS)).isTrue();
        streamingHandle.get().cancel();
        assertThat(compensated).succeedsWithin(10, SECONDS);
        releaseFinalAnswer.countDown();
        for (Thread processingThread : processingThreads) {
            processingThread.join(10_000);
        }

        assertThat(bank.calls).containsExactly("credit:Mario", "uncredit:Mario");
        assertThat(completed).isNotDone();
        assertThat(memory.messages())
                .noneMatch(message -> message instanceof AiMessage aiMessage && "done".equals(aiMessage.text()));
    }

    @Test
    void token_stream_should_roll_back_a_tool_of_the_cancelled_round_once_it_has_finished() {

        CancellingBank bank = new CancellingBank();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        // everything runs on the calling thread, so the invocation is over once start() returns; the rest of the
        // response still arrives after the cancellation
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from("Crediting", List.of(CREDIT)), AiMessage.from("done"));

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(StreamingTestModels.streamingOnCallingThreadEvenAfterCancellation(model))
                .chatMemory(memory)
                .tools(bank)
                // the tool is started on its complete tool call, and runs on the thread that streams the response
                .executeToolsConcurrently(Runnable::run)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add)
                .build();

        CompletableFuture<Object> completed = new CompletableFuture<>();
        assistant
                .chat("transfer")
                .onPartialResponseWithContext(
                        (partialResponse, context) -> bank.streamingHandle = context.streamingHandle())
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start();

        assertThat(bank.calls).containsExactly("credit:Mario", "credit finished", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> {
            assertThat(event.request().name()).isEqualTo("credit");
            assertThat(event.reason()).isEqualTo(CompensationReason.INVOCATION_CANCELLED);
        });
        // the response that arrived after the cancellation was not processed
        assertThat(memory.messages()).noneMatch(message -> message instanceof AiMessage);
        assertThat(model.requests()).hasSize(1);
        assertThat(completed).isNotDone();
    }

    @Test
    void token_stream_should_not_start_another_tool_of_the_round_after_the_cancellation() {

        CancellingBank bank = new CancellingBank();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        // the complete tool call of withdraw still arrives after credit cancelled the invocation
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from("Paying", List.of(CREDIT, WITHDRAW)), AiMessage.from("done"));

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(StreamingTestModels.streamingOnCallingThreadEvenAfterCancellation(model))
                .tools(bank)
                .executeToolsConcurrently(Runnable::run)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add)
                .build();

        assistant
                .chat("transfer")
                .onPartialResponseWithContext(
                        (partialResponse, context) -> bank.streamingHandle = context.streamingHandle())
                .onError(ignored -> {})
                .start();

        assertThat(bank.calls).containsExactly("credit:Mario", "credit finished", "uncredit:Mario");
        assertThat(compensatedEvents).hasSize(1);
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void token_stream_should_roll_back_a_tool_still_running_when_cancelled_exactly_once() throws Exception {

        SlowBank bank = new SlowBank();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        CountDownLatch responseReceived = new CountDownLatch(1);
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from("Crediting", List.of(CREDIT)), AiMessage.from("done"));

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(StreamingTestModels.streamingOnCallingThread(model))
                .chatMemory(memory)
                .tools(bank)
                .executeToolsConcurrently()
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add)
                // fired once the processing of the response has claimed it, before it waits for the running tool
                .registerListener((AiServiceResponseReceivedListener) event -> responseReceived.countDown())
                .build();

        AtomicReference<StreamingHandle> streamingHandle = new AtomicReference<>();
        CompletableFuture<Object> completed = new CompletableFuture<>();
        Thread streamingThread = new Thread(() -> assistant
                .chat("transfer")
                .onPartialResponseWithContext(
                        (partialResponse, context) -> streamingHandle.set(context.streamingHandle()))
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start());
        streamingThread.start();

        assertThat(responseReceived.await(10, SECONDS)).isTrue();
        streamingHandle.get().cancel();
        bank.release.countDown();
        streamingThread.join(10_000);

        assertThat(bank.calls).containsExactly("credit:Mario", "credit finished", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> assertThat(event.reason())
                .isEqualTo(CompensationReason.INVOCATION_CANCELLED));
        // the result was saved before the rollback, so the tool call in the chat memory has a result
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLED_BACK_ON_CANCELLATION);
        assertThat(model.requests()).hasSize(1);
        assertThat(completed).isNotDone();
    }

    @Test
    void token_stream_should_finish_and_save_the_round_before_rolling_back_when_cancelled_during_sequential_tools() {

        CancellingBank bank = new CancellingBank();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from("Paying", List.of(CREDIT, WITHDRAW)), AiMessage.from("done"));

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(StreamingTestModels.streamingOnCallingThread(model))
                .chatMemory(memory)
                .tools(bank)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add)
                .build();

        CompletableFuture<Object> completed = new CompletableFuture<>();
        assistant
                .chat("transfer")
                .onPartialResponseWithContext(
                        (partialResponse, context) -> bank.streamingHandle = context.streamingHandle())
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start();

        // credit cancels the invocation while the tools of the round run one after the other
        assertThat(bank.calls)
                .containsExactly("credit:Mario", "credit finished", "withdraw:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> {
            assertThat(event.request().name()).isEqualTo("credit");
            assertThat(event.reason()).isEqualTo(CompensationReason.INVOCATION_CANCELLED);
        });
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLED_BACK_ON_CANCELLATION);
        assertThat(toolResultTexts(memory, "withdraw")).containsExactly("withdrawn");
        assertThat(model.requests()).hasSize(1);
        assertThat(completed).isNotDone();
    }

    @ParameterizedTest
    @EnumSource(CancellationWhileSaving.class)
    void token_stream_should_roll_back_and_stop_when_cancelled_while_the_results_of_a_round_are_saved(
            CancellationWhileSaving moment) {

        Bank bank = new Bank();
        MemoryCancellingWhileSaving memory = new MemoryCancellingWhileSaving(moment);
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        AtomicInteger cancelledEvents = new AtomicInteger();
        // everything runs on the calling thread, so the invocation is over once start() returns
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from("Crediting", List.of(CREDIT)), AiMessage.from("done"));

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(StreamingTestModels.streamingOnCallingThread(model))
                .chatMemory(memory)
                .tools(bank)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add)
                .registerListener((AiServiceCancelledListener) event -> cancelledEvents.incrementAndGet())
                .build();

        CompletableFuture<Object> completed = new CompletableFuture<>();
        assistant
                .chat("transfer")
                .onPartialResponseWithContext(
                        (partialResponse, context) -> memory.streamingHandle = context.streamingHandle())
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start();

        assertThat(bank.calls).containsExactly("credit:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> {
            assertThat(event.request().name()).isEqualTo("credit");
            assertThat(event.reason()).isEqualTo(CompensationReason.INVOCATION_CANCELLED);
        });
        assertThat(cancelledEvents).hasValue(1);
        // the result was saved before the rollback, so the tool call in the chat memory has a result
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLED_BACK_ON_CANCELLATION);
        // no next round
        assertThat(model.requests()).hasSize(1);
        assertThat(completed).isNotDone();
    }

    @Test
    void token_stream_should_roll_back_once_when_cancelled_while_saving_a_round_whose_tool_error_is_sent_to_the_llm() {

        Bank bank = new Bank();
        MemoryCancellingWhileSaving memory =
                new MemoryCancellingWhileSaving(CancellationWhileSaving.WHILE_THE_FIRST_RESULT_IS_ADDED);
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from("Paying", List.of(CREDIT, WITHDRAW)), AiMessage.from("done"));

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(StreamingTestModels.streamingOnCallingThread(model))
                .chatMemory(memory)
                .tools(bank)
                .compensateOnToolErrors(true)
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.sendExceptionMessageToLlm())
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add)
                .build();

        CompletableFuture<Object> completed = new CompletableFuture<>();
        assistant
                .chat("transfer")
                .onPartialResponseWithContext(
                        (partialResponse, context) -> memory.streamingHandle = context.streamingHandle())
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start();

        // the failed withdraw rolled back credit before the results were saved, so the cancellation has nothing left
        assertThat(bank.calls).containsExactly("credit:Mario", "withdraw:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> assertThat(event.reason())
                .isEqualTo(CompensationReason.TOOL_EXECUTION_FAILED));
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLED_BACK);
        assertThat(model.requests()).hasSize(1);
        assertThat(completed).isNotDone();
    }

    @Test
    void token_stream_should_roll_back_when_cancelled_during_a_round_whose_results_cannot_be_saved() {

        CancellingBank bank = new CancellingBank();
        MemoryFailingToSaveToolResults memory = new MemoryFailingToSaveToolResults();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from("Paying", List.of(CREDIT, WITHDRAW)), AiMessage.from("done"));

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(StreamingTestModels.streamingOnCallingThread(model))
                .chatMemory(memory)
                .tools(bank)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add)
                .build();

        CompletableFuture<Object> completed = new CompletableFuture<>();
        assistant
                .chat("transfer")
                .onPartialResponseWithContext(
                        (partialResponse, context) -> bank.streamingHandle = context.streamingHandle())
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start();

        // credit cancels the invocation while the round runs, and then its results cannot be saved
        assertThat(bank.calls)
                .containsExactly("credit:Mario", "credit finished", "withdraw:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> assertThat(event.reason())
                .isEqualTo(CompensationReason.INVOCATION_CANCELLED));
        assertThat(completed)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isSameAs(memory.failure);
        assertThat(model.requests()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(SavingFailure.class)
    void token_stream_should_rewrite_the_saved_results_of_a_cancelled_round_when_saving_them_fails(
            SavingFailure savingFailure) {

        CancellingBank bank = new CancellingBank();
        MemoryFailingAfterTheFirstToolResult memory = new MemoryFailingAfterTheFirstToolResult(
                savingFailure, () -> bank.calls.contains("uncredit:Mario"));
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from("Paying", List.of(CREDIT, WITHDRAW)), AiMessage.from("done"));

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(StreamingTestModels.streamingOnCallingThread(model))
                .chatMemory(memory)
                .tools(bank)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add)
                .build();

        assistant
                .chat("transfer")
                .onPartialResponseWithContext(
                        (partialResponse, context) -> bank.streamingHandle = context.streamingHandle())
                .onError(ignored -> {})
                .start();

        // credit cancels the invocation while the round runs; the result of credit is saved, and then saving fails
        assertThat(bank.calls)
                .containsExactly("credit:Mario", "credit finished", "withdraw:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> assertThat(event.reason())
                .isEqualTo(CompensationReason.INVOCATION_CANCELLED));
        assertThat(toolResultTexts(memory, "credit")).containsExactly(CREDIT_ROLLED_BACK_ON_CANCELLATION);
        if (savingFailure == SavingFailure.ADDING_THE_SECOND_RESULT) {
            assertThat(toolResultTexts(memory, "withdraw")).isEmpty();
        } else {
            assertThat(toolResultTexts(memory, "withdraw")).containsExactly("withdrawn");
        }
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void token_stream_cancel_should_roll_back_on_the_calling_thread_when_the_complete_response_processing_executor_rejects_the_rollback() {

        Bank bank = new Bank();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from(CREDIT), AiMessage.from("Withdrawing", List.of(WITHDRAW)));

        // the processing of the first response runs, the rollback is rejected
        TokenStream tokenStream = tokenStream(
                model,
                bank,
                new ExecutorRejectingAfter(1),
                context -> context.eventListenerRegistrar.register(
                        (ToolCompensatedEventListener) compensatedEvents::add));

        CompletableFuture<Throwable> cancelFailure = new CompletableFuture<>();
        tokenStream
                .onPartialResponseWithContext((partialResponse, context) -> {
                    try {
                        context.streamingHandle().cancel();
                        cancelFailure.complete(null);
                    } catch (RuntimeException e) {
                        cancelFailure.complete(e);
                    }
                })
                .onError(ignored -> {})
                .start();

        assertThat(cancelFailure).succeedsWithin(10, SECONDS).isNull();
        // the rejected rollback runs on the thread that cancels
        assertThat(bank.calls).containsExactly("credit:Mario", "uncredit:Mario");
        assertThat(compensatedEvents).singleElement().satisfies(event -> assertThat(event.reason())
                .isEqualTo(CompensationReason.INVOCATION_CANCELLED));
    }

    @Test
    void token_stream_cancel_should_return_normally_when_the_chat_memory_cannot_be_rewritten() {

        Bank bank = new Bank();
        ChatMemory memory = new MemoryFailingOnRewrite();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from(CREDIT), AiMessage.from("Withdrawing", List.of(WITHDRAW)));

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(model)
                .chatMemory(memory)
                .tools(bank)
                .compensateOnToolErrors(true)
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add)
                .build();

        CompletableFuture<Throwable> cancelFailure = new CompletableFuture<>();
        assistant
                .chat("transfer")
                .onPartialResponseWithContext((partialResponse, context) -> {
                    try {
                        context.streamingHandle().cancel();
                        cancelFailure.complete(null);
                    } catch (RuntimeException e) {
                        cancelFailure.complete(e);
                    }
                })
                .onError(ignored -> {})
                .start();

        // no tool was running, so the rollback ran on the thread that cancelled, before cancel() returned
        assertThat(cancelFailure).succeedsWithin(10, SECONDS).isNull();
        assertThat(bank.calls).containsExactly("credit:Mario", "uncredit:Mario");
        // the failed rewrite is logged, and the event is fired anyway
        assertThat(toolResultTexts(memory, "credit")).containsExactly("credited");
        assertThat(compensatedEvents).singleElement().satisfies(event -> {
            assertThat(event.request().name()).isEqualTo("credit");
            assertThat(event.reason()).isEqualTo(CompensationReason.INVOCATION_CANCELLED);
        });
    }

    @Test
    void token_stream_should_not_roll_back_when_cancelled_without_compensation() {

        Bank bank = new Bank();
        List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                AiMessage.from(CREDIT), AiMessage.from("Withdrawing", List.of(WITHDRAW)), AiMessage.from("done"));

        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(model)
                .tools(bank)
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add)
                .build();

        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        assistant
                .chat("transfer")
                .onPartialResponseWithContext((partialResponse, context) -> {
                    context.streamingHandle().cancel();
                    cancelled.complete(null);
                })
                .onError(ignored -> {})
                .start();

        assertThat(cancelled).succeedsWithin(10, SECONDS);
        assertThat(bank.calls).containsExactly("credit:Mario");
        assertThat(compensatedEvents).isEmpty();
    }

    private static void assertCallsOfFailedRollback(BankWithFailingUncredit bank) {
        // credit and withdraw run in the same round, at the same time in the reactive mode
        assertThat(bank.calls)
                .containsExactlyInAnyOrder(
                        "reserve:Mario", "credit:Mario", "withdraw:Mario", "uncredit:Mario", "unreserve:Mario")
                .startsWith("reserve:Mario")
                .endsWith("uncredit:Mario", "unreserve:Mario");
    }

    private static void assertCompensationErrorsOfFailedRollback(
            List<ToolCompensatedEvent> compensatedEvents, BankWithFailingUncredit bank) {
        assertThat(compensatedEvents)
                .extracting(event -> event.request().name())
                .containsExactlyInAnyOrder("reserve", "credit");
        assertThat(compensatedEvents).allSatisfy(event -> {
            if (event.request().name().equals("credit")) {
                assertThat(event.compensationError()).isSameAs(bank.uncreditFailure);
            } else {
                assertThat(event.compensationError()).isNull();
            }
        });
    }

    // the error the invocation failed with, as the caller gets it
    private static Throwable failureOf(CompletableFuture<String> answer) throws Exception {
        Throwable failure = answer.handle((result, error) -> error).get(10, SECONDS);
        while (failure instanceof CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        assertThat(failure).isNotNull();
        return failure;
    }

    private static void assertInvalidArgumentsFailure(CompletableFuture<String> answer) {
        assertThat(answer)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .withMessageContaining("Unexpected character ('i'");
    }

    private static CompletableFuture<String> chat(
            Mode mode, List<AiMessage> responses, Consumer<AiServices<?>> configurer) {
        AiMessage[] aiMessages = responses.toArray(new AiMessage[0]);
        return switch (mode) {
            case SYNC -> {
                AiServices<SyncAssistant> aiServices =
                        AiServices.builder(SyncAssistant.class).chatModel(chatModel(aiMessages));
                configurer.accept(aiServices);
                SyncAssistant assistant = aiServices.build();
                try {
                    yield CompletableFuture.completedFuture(assistant.chat("transfer"));
                } catch (RuntimeException e) {
                    yield CompletableFuture.failedFuture(e);
                }
            }
            case TOKEN_STREAM -> {
                AiServices<TokenStreamAssistant> aiServices = AiServices.builder(TokenStreamAssistant.class)
                        .streamingChatModel(streamingChatModel(aiMessages));
                configurer.accept(aiServices);
                CompletableFuture<String> answer = new CompletableFuture<>();
                aiServices
                        .build()
                        .chat("transfer")
                        .onPartialResponse(ignored -> {})
                        .onCompleteResponse(response -> answer.complete(response.aiMessage().text()))
                        .onError(answer::completeExceptionally)
                        .start();
                yield answer;
            }
            case COMPLETABLE_FUTURE -> {
                AiServices<AsyncAssistant> aiServices =
                        AiServices.builder(AsyncAssistant.class).chatModel(chatModel(aiMessages));
                configurer.accept(aiServices);
                yield aiServices.build().chat("transfer");
            }
            case REACTIVE -> {
                AiServices<ReactiveAssistant> aiServices = AiServices.builder(ReactiveAssistant.class)
                        .streamingChatModel(StreamingEventChatModelMock.thatStreams(aiMessages));
                configurer.accept(aiServices);
                StringCollector collector = new StringCollector();
                aiServices.build().chat("transfer").subscribe(collector);
                yield collector.answer;
            }
        };
    }

    // the mock gives each tool call without an id an id of its own, so a script with tool calls without an id is
    // answered without ids, as by a provider that gives tool calls no id
    private static ChatModel chatModel(AiMessage[] aiMessages) {
        MockChatModel model = MockChatModel.thatResponds(aiMessages);
        return hasToolCallsWithoutId(aiMessages) ? withoutToolCallIds(model) : model;
    }

    private static StreamingChatModel streamingChatModel(AiMessage[] aiMessages) {
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(aiMessages);
        return hasToolCallsWithoutId(aiMessages) ? StreamingTestModels.withoutToolCallIds(model) : model;
    }

    private static boolean hasToolCallsWithoutId(AiMessage[] aiMessages) {
        return Stream.of(aiMessages)
                .flatMap(aiMessage -> aiMessage.toolExecutionRequests().stream())
                .anyMatch(toolCall -> toolCall.id() == null);
    }

    @SuppressWarnings("unchecked")
    private static ChatModel withoutToolCallIds(MockChatModel model) {
        MockChatModel withoutIds = spy(model);
        doAnswer(invocation -> withoutToolCallIds((ChatResponse) invocation.callRealMethod()))
                .when(withoutIds)
                .doChat(any(ChatRequest.class));
        doAnswer(invocation -> ((CompletableFuture<ChatResponse>) invocation.callRealMethod())
                        .thenApply(AiServiceToolCompensationTest::withoutToolCallIds))
                .when(withoutIds)
                .doChatAsync(any(ChatRequest.class));
        return withoutIds;
    }

    private static ChatResponse withoutToolCallIds(ChatResponse response) {
        AiMessage aiMessage = response.aiMessage();
        if (!aiMessage.hasToolExecutionRequests()) {
            return response;
        }
        List<ToolExecutionRequest> toolCalls = aiMessage.toolExecutionRequests().stream()
                .map(toolCall -> toolCall.toBuilder().id(null).build())
                .toList();
        return response.toBuilder()
                .aiMessage(aiMessage.toBuilder().toolExecutionRequests(toolCalls).build())
                .build();
    }

    // starts the invocation in a non-blocking mode, and returns what cancels it
    private static Runnable startCancellable(
            Mode mode, List<AiMessage> responses, Consumer<AiServices<?>> configurer) {
        AiMessage[] aiMessages = responses.toArray(new AiMessage[0]);
        if (mode == Mode.COMPLETABLE_FUTURE) {
            AiServices<AsyncAssistant> aiServices =
                    AiServices.builder(AsyncAssistant.class).chatModel(chatModel(aiMessages));
            configurer.accept(aiServices);
            CompletableFuture<String> answer = aiServices.build().chat("transfer");
            return () -> answer.cancel(true);
        }
        assertThat(mode).isEqualTo(Mode.REACTIVE);
        AiServices<ReactiveAssistant> aiServices = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingEventChatModelMock.thatStreams(aiMessages));
        configurer.accept(aiServices);
        StringCollector collector = new StringCollector();
        aiServices.build().chat("transfer").subscribe(collector);
        return () -> collector.subscription.cancel();
    }

    private static TokenStream tokenStream(
            StreamingChatModel model,
            Object tools,
            Executor completeResponseProcessingExecutor,
            Consumer<AiServiceContext> configurer) {
        return tokenStream(model, tools, completeResponseProcessingExecutor, null, configurer);
    }

    private static TokenStream tokenStream(
            StreamingChatModel model,
            Object tools,
            Executor completeResponseProcessingExecutor,
            ChatMemory chatMemory,
            Consumer<AiServiceContext> configurer) {
        return tokenStream(model, tools, completeResponseProcessingExecutor, chatMemory, null, configurer);
    }

    // built directly, to set what AiServices does not offer (a complete response processing executor, a listener
    // registrar that rethrows the errors of the listeners, the hooks of cross-agent compensation)
    private static TokenStream tokenStream(
            StreamingChatModel model,
            Object tools,
            Executor completeResponseProcessingExecutor,
            ChatMemory chatMemory,
            Executor toolExecutor,
            Consumer<AiServiceContext> configurer) {
        AiServiceContext context = AiServiceContext.create(TokenStreamAssistant.class);
        context.streamingChatModel = model;
        context.toolService.compensateOnToolErrors(true);
        context.toolService.tools(List.of(tools));
        if (chatMemory != null) {
            context.initChatMemories(memoryId -> chatMemory);
        }
        configurer.accept(context);

        UserMessage userMessage = UserMessage.from("transfer");
        List<ChatMessage> messages = List.of(userMessage);
        InvocationContext invocationContext = InvocationContext.builder()
                .interfaceName(TokenStreamAssistant.class.getName())
                .methodName("chat")
                .chatMemoryId("default")
                .userMessage(userMessage)
                .build();
        ToolServiceContext toolServiceContext =
                context.toolService.createContext(invocationContext, userMessage, messages);

        return new AiServiceTokenStream(AiServiceTokenStreamParameters.builder()
                .messages(messages)
                .toolServiceContext(toolServiceContext)
                .toolArgumentsErrorHandler(ToolArgumentsErrorHandler.failInvocation())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.sendExceptionMessageToLlm())
                .completeResponseProcessingExecutor(completeResponseProcessingExecutor)
                .toolExecutor(toolExecutor)
                .context(context)
                .invocationContext(invocationContext)
                .build());
    }

    private static List<String> toolResultTexts(ChatMemory memory, String toolName) {
        return memory.messages().stream()
                .filter(message -> message instanceof ToolExecutionResultMessage result
                        && Objects.equals(result.toolName(), toolName))
                .map(message -> ((ToolExecutionResultMessage) message).text())
                .toList();
    }

    static class StringCollector implements Flow.Subscriber<String> {

        final CompletableFuture<String> answer = new CompletableFuture<>();
        private final StringBuilder text = new StringBuilder();
        volatile Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String token) {
            text.append(token);
        }

        @Override
        public void onError(Throwable throwable) {
            answer.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            answer.complete(text.toString());
        }
    }
}
