package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.CompletableFutureUtils.failOnThrow;
import static dev.langchain4j.internal.Exceptions.restoreInterruptIfInterrupted;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.ToolInputGuardrailException;
import dev.langchain4j.guardrail.ToolOutputGuardrailException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.CompensationReason;
import dev.langchain4j.service.guardrail.GuardedToolExecution;
import dev.langchain4j.service.guardrail.ToolGuardrailStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;

/**
 * One round of tool execution: the tool calls of one {@link AiMessage}, in every AI service mode (blocking,
 * {@code CompletableFuture}, {@code TokenStream}, reactive). A round is started by
 * {@link ToolService#startRound(ToolServiceContext, InvocationContext, AiServiceListenerRegistrar)} and is the single
 * way in and out of executing its calls:
 * <ul>
 *     <li><b>in:</b> {@link #executeTool(ToolExecutionRequest, Consumer, Consumer)} (blocking, on the calling thread or
 *     on an executor) or {@link #startTool(ToolExecutionRequest, Consumer, Consumer, Executor)} (non-blocking)
 *     executes a call, through the tool guardrails of its tool if it has any;</li>
 *     <li><b>out:</b> once every call has run, {@link #complete(AiMessage, List, ChatMemory, List)} or
 *     {@link #completeAsync(AiMessage, List, ChatMemory, List)} reports the round: the calls as they were executed,
 *     their results, and the messages for the rest of the round ({@link #settleAsync(List)} for a canceled round,
 *     and {@link #failBeforeRethrow(List, Throwable, List, ChatMemory, List)} for a blocking round that fails with an
 *     exception).</li>
 * </ul>
 * <p>
 *     The loop does not need to know that the calls it gets back can differ from the ones of the LLM: an input
 *     guardrail can rewrite the arguments of a call, and the rewritten call is then what everything downstream
 *     carries, including the tool call stored in the history, which completing the round updates.
 * </p>
 * <p>
 *     A round can start before the {@link AiMessage} is known (a streaming model announces each tool call before its
 *     final response), so the calls are identified by the requests they were executed with, and the caller passes
 *     them, in the order of the LLM, when it completes the round. The tools of a round may run concurrently, so
 *     executing and recording calls is thread-safe.
 * </p>
 *
 * @since 1.25.0
 */
@Internal
public final class ToolRound {

    private static final Logger log = LoggerFactory.getLogger(ToolRound.class);

    private final ToolService toolService;
    private final ToolServiceContext toolServiceContext;
    private final InvocationContext invocationContext;
    private final AiServiceListenerRegistrar listenerRegistrar;
    private final CompletableFuture<?> cancellation;
    private final Map<ToolExecutionRequest, GuardedToolExecution> executions = new ConcurrentHashMap<>();
    private final Map<ToolExecutionRequest, ToolExecutionRequest> startedRequests = new ConcurrentHashMap<>();
    private final Map<ToolExecutionRequest, ToolExecutionResult> toolResults = new ConcurrentHashMap<>();
    // the failure of each call that ended with an exception, recorded at the entries of the round only
    private final Map<ToolExecutionRequest, Throwable> errors = new ConcurrentHashMap<>();
    // the future of each call started without blocking, which settles once its failure is recorded
    private final Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> futures = new ConcurrentHashMap<>();

    private ToolRound(
            ToolService toolService,
            ToolServiceContext toolServiceContext,
            InvocationContext invocationContext,
            AiServiceListenerRegistrar listenerRegistrar,
            CompletableFuture<?> cancellation) {
        this.toolService = ensureNotNull(toolService, "toolService");
        this.toolServiceContext = toolServiceContext;
        this.invocationContext = invocationContext;
        this.listenerRegistrar = listenerRegistrar;
        this.cancellation = cancellation;
    }

    /**
     * Starts a round of {@code toolService}; see {@link ToolService#startRound(ToolServiceContext, InvocationContext,
     * AiServiceListenerRegistrar, CompletableFuture)}.
     *
     * @param toolServiceContext the context of the tools of the round; {@code null} for a round without tools
     * @param listenerRegistrar the registrar of the invocation, for the events of the round, and of the tool guardrails
     *                          when the context carries no {@link GuardrailRequestParams}; may be {@code null}
     * @param cancellation canceled when the invocation is canceled; may be {@code null}
     */
    static ToolRound start(
            ToolService toolService,
            ToolServiceContext toolServiceContext,
            InvocationContext invocationContext,
            AiServiceListenerRegistrar listenerRegistrar,
            CompletableFuture<?> cancellation) {
        return new ToolRound(toolService, toolServiceContext, invocationContext, listenerRegistrar, cancellation);
    }

    /**
     * Executes a call of the round, blocking: its tool, through the tool guardrails of the tool if it has any, with
     * the callbacks of the {@link ToolService} and the given ones. Every exception that ends the call is thrown (a
     * fatal guardrail failure, an error of the tool that its error handler rethrows, a failing callback), and the round
     * records it, for {@link #failBeforeRethrow(List, Throwable, List, ChatMemory, List)}; a {@link VirtualMachineError}
     * is rethrown without being recorded.
     *
     * @param call                the call of the LLM
     * @param beforeToolExecution called before the call is executed, with the call of the LLM; may be {@code null}
     * @param afterToolExecution  called after the call is executed, with the request it was executed with and its
     *                            result; may be {@code null}
     * @return the request the call was executed with, and its result
     */
    public GuardedToolExecution executeTool(
            ToolExecutionRequest call,
            Consumer<BeforeToolExecution> beforeToolExecution,
            Consumer<ToolExecution> afterToolExecution) {
        try {
            return toolService.executeTool(
                    this, invocationContext, toolExecutors(), call, beforeToolExecution, afterToolExecution);
        } catch (VirtualMachineError e) {
            // the JVM may not be able to run more code safely: rethrown without recording, as the loops do
            throw e;
        } catch (RuntimeException | Error e) {
            errors.put(call, e);
            throw e;
        }
    }

    /**
     * Executes a call of the round on a thread of {@code executor}, blocking that thread, as
     * {@link #executeTool(ToolExecutionRequest, Consumer, Consumer)} does. Nothing is thrown: every exception that ends
     * the call fails the returned future, and the round records it. When {@code executor} rejects the call, the call
     * fails with the rejection, so the round reports it like a call that threw.
     *
     * @return the future of the request the call was executed with, and its result
     */
    public CompletableFuture<GuardedToolExecution> executeTool(
            ToolExecutionRequest call,
            Consumer<BeforeToolExecution> beforeToolExecution,
            Consumer<ToolExecution> afterToolExecution,
            Executor executor) {
        CompletableFuture<GuardedToolExecution> execution;
        try {
            execution = CompletableFuture.supplyAsync(
                    () -> executeTool(call, beforeToolExecution, afterToolExecution), executor);
        } catch (RuntimeException rejected) {
            errors.put(call, rejected);
            execution = CompletableFuture.failedFuture(rejected);
        }
        return execution;
    }

    /**
     * Starts a call of the round on {@code executor}, as {@link #executeTool(ToolExecutionRequest, Consumer, Consumer)}
     * does, without blocking. Nothing is thrown: every failure fails the returned future, and the round records it.
     * Once the round is canceled, the call fails with a {@link CancellationException} instead of starting its tool.
     *
     * @return the future of the result of the call
     */
    public CompletableFuture<ToolExecutionResult> startTool(
            ToolExecutionRequest call,
            Consumer<BeforeToolExecution> beforeToolExecution,
            Consumer<ToolExecution> afterToolExecution,
            Executor executor) {
        return failOnThrow(() -> {
            ensureNotNull(call, "call");
            var started = toolService.startTool(
                    this, call, toolExecutors(), invocationContext, beforeToolExecution, afterToolExecution, executor);
            // the round waits for this future, which completes only once the failure is recorded
            CompletableFuture<ToolExecutionResult> recorded = started.whenComplete((ignored, error) -> {
                if (error != null) {
                    errors.put(call, unwrapCompletionException(error));
                }
            });
            propagateCancellation(recorded, started);
            futures.put(call, recorded);
            return recorded;
        });
    }

    /**
     * Fails a blocking round whose calls have all ended, when a call failed with an exception that ends the invocation,
     * right before the caller rethrows {@code errorToRethrow}, the way the non-blocking modes fail a round: it fires a
     * {@link ToolExecutedEvent} for each call that ended, with the request it
     * was executed with, tells cross-agent compensation about the calls that ran successfully and that a call failed,
     * and rolls back the compensable tools that ran successfully in this round and in the earlier rounds, when
     * compensation on tool errors is enabled. A failure of a listener, of cross-agent compensation or of the rollback
     * is added as suppressed to {@code errorToRethrow}, and so is the failure of a compensating action, which does not
     * stop the others.
     *
     * @param calls                 the calls of the round, as they were started, in the order of the LLM
     * @param errorToRethrow        the exception the caller rethrows once this method returns
     * @param compensableExecutions the compensable tools collected across rounds, or {@code null} when compensation is
     *                              disabled
     * @param chatMemory            the chat memory, or {@code null} when the AI service has none
     * @param messages              the messages of the invocation, rewritten when {@code chatMemory} is {@code null}
     */
    public void failBeforeRethrow(
            List<ToolExecutionRequest> calls,
            Throwable errorToRethrow,
            List<ToolService.CompensableToolExecution> compensableExecutions,
            ChatMemory chatMemory,
            List<ChatMessage> messages) {
        ToolService.fireEventsOfFailedRound(invocationContext, calls, this, listenerRegistrar);
        toolService.failRoundBeforeRethrow(
                this, settled(calls, List.of()), errorToRethrow, compensableExecutions, chatMemory, messages);
    }

    AiServiceListenerRegistrar listenerRegistrar() {
        return listenerRegistrar;
    }

    InvocationContext invocationContext() {
        return invocationContext;
    }

    /**
     * The guarded result of a call that was executed, empty if it was not (yet).
     */
    Optional<ToolExecutionResult> result(ToolExecutionRequest call) {
        return Optional.ofNullable(executions.get(call)).map(GuardedToolExecution::result);
    }

    /**
     * The failure of a call that ended with an exception, empty if it did not.
     */
    Optional<Throwable> error(ToolExecutionRequest call) {
        return Optional.ofNullable(errors.get(call));
    }

    private Map<String, ToolExecutor> toolExecutors() {
        return (this.toolServiceContext != null) ? this.toolServiceContext.toolExecutors() : Map.of();
    }

    /**
     * Executes a call of a tool that exists, through its guardrails if it has any: the input guardrails, then
     * {@code tool}, then the output guardrails. A fatal guardrail failure is thrown from here, outside {@code tool}.
     * Arguments that an input guardrail could not parse ({@link ToolArgumentsException}) are handled by
     * {@code argumentsError}, as an arguments error of the tool would be: the tool is not executed.
     *
     * @param call           the call of the LLM
     * @param tool           executes the tool for a request (the call, or the call as the input guardrails rewrote
     *                       it), handling its errors
     * @param argumentsError handles the arguments error of an input guardrail, before the tool started
     */
    GuardedToolExecution execute(
            ToolExecutionRequest call,
            Function<ToolExecutionRequest, ToolExecutionResult> tool,
            Function<ToolArgumentsException, ToolExecutionResult> argumentsError) {
        Function<ToolExecutionRequest, ToolExecutionResult> recordingTool = request -> {
            startedRequests.put(call, request);
            var toolResult = tool.apply(request);
            toolResults.put(call, toolResult);
            return toolResult;
        };

        GuardedToolExecution execution;
        try {
            execution = binding(call.name())
                    .map(binding -> binding.service()
                            .executeGuardrails(
                                    binding.key(), call, guardrailRequestParams(), recordingTool))
                    .orElseGet(() -> unguarded(call, recordingTool.apply(call)));
        } catch (ToolArgumentsException e) {
            // once the tool has started, its own error handling already ran
            if (startedRequests.containsKey(call)) {
                throw e;
            }
            execution = unguarded(call, argumentsError.apply(e));
        }

        executions.put(call, execution);
        return execution;
    }

    /**
     * The arguments error of an input guardrail, if {@code error} is one and the tool of the call has not started.
     */
    private Optional<ToolArgumentsException> argumentsErrorBeforeTool(ToolExecutionRequest call, Throwable error) {
        return Optional.of(unwrapCompletionException(error))
                .filter(ToolArgumentsException.class::isInstance)
                .map(ToolArgumentsException.class::cast)
                .filter(ignored -> !startedRequests.containsKey(call));
    }

    /**
     * Non-blocking counterpart of {@link #execute(ToolExecutionRequest, Function, Function)}. Nothing is thrown: a
     * fatal guardrail failure, or a failure of {@code tool}, fails the returned future. Once the round is canceled, no
     * tool is started any more, and a call whose input guardrails are in flight is canceled.
     *
     * @param call           the call of the LLM
     * @param tool           starts the tool for a request (the call, or the call as the input guardrails rewrote
     *                       it), handling its errors
     * @param argumentsError handles the arguments error of an input guardrail, before the tool started
     * @param executor       the executor of the tools: the tool is started on it when the input guardrails complete
     *                       on another thread, so it never runs on a thread of a guardrail
     * @return the future of the result to send to the LLM
     */
    CompletableFuture<ToolExecutionResult> executeAsync(
            ToolExecutionRequest call,
            Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> tool,
            Function<ToolArgumentsException, CompletableFuture<ToolExecutionResult>> argumentsError,
            Executor executor) {

        Thread startingThread = Thread.currentThread();
        AtomicBoolean starting = new AtomicBoolean(true);
        Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> recordingTool = request -> {
            // recorded before checking the cancellation, so that a cancellation either sees the tool as started (and
            // lets it run to completion) or stops it here
            startedRequests.put(call, request);
            CompletableFuture<ToolExecutionResult> recorded;
            if (isCancelled()) {
                recorded = CompletableFuture.failedFuture(new CancellationException());
            } else {
                // an input guardrail that completed on another thread must not run the tool on that thread
                boolean onStartingThread = starting.get() && Thread.currentThread() == startingThread;
                var toolResult = (onStartingThread || executor == null)
                        ? failOnThrow(tool, "apply(ToolExecutionRequest)", () -> tool.apply(request))
                        : startOn(executor, tool, request);
                recorded = toolResult.thenApply(result -> {
                    toolResults.put(call, result);
                    return result;
                });
                propagateCancellation(recorded, toolResult);
            }
            return recorded;
        };

        CompletableFuture<GuardedToolExecution> execution;
        if (isCancelled()) {
            execution = CompletableFuture.failedFuture(new CancellationException());
        } else {
            execution = binding(call.name())
                    .map(binding -> failOnThrow(
                            binding.service(),
                            "executeGuardrailsAsync(ToolKey, ToolExecutionRequest, GuardrailRequestParams, Function)",
                            () -> binding.service()
                                    .executeGuardrailsAsync(
                                            binding.key(), call, guardrailRequestParams(), recordingTool)))
                    .orElseGet(() -> recordingTool.apply(call).thenApply(result -> unguarded(call, result)));
            var guarded = execution;
            execution = guarded.exceptionallyCompose(error -> argumentsErrorBeforeTool(call, error)
                    .map(argumentsException ->
                            argumentsError.apply(argumentsException).thenApply(result -> unguarded(call, result)))
                    .orElseGet(() -> CompletableFuture.failedFuture(error)));
            propagateCancellation(execution, guarded);
            cancelBeforeToolStarts(call, execution);
        }
        starting.set(false);
        return track(call, execution);
    }

    /**
     * Starts the tool on the executor. Canceling the returned future cancels the future of the tool. The cancellation
     * is checked again when the task starts on the executor: a tool that waited in its queue while the returned
     * future or the round was canceled does not start.
     */
    private CompletableFuture<ToolExecutionResult> startOn(
            Executor executor,
            Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> tool,
            ToolExecutionRequest request) {
        var result = new CompletableFuture<ToolExecutionResult>();
        try {
            executor.execute(() -> {
                // the task may have waited in the queue of the executor: a cancellation that came meanwhile, of the
                // returned future or of the round, keeps the tool from starting
                if (isCancelled()) {
                    result.completeExceptionally(new CancellationException());
                } else if (!result.isDone()) {
                    var toolResult = failOnThrow(tool, "apply(ToolExecutionRequest)", () -> tool.apply(request));
                    propagateCancellation(result, toolResult);
                    toolResult.whenComplete((value, error) -> {
                        if (error != null) {
                            result.completeExceptionally(unwrapCompletionException(error));
                        } else {
                            result.complete(value);
                        }
                    });
                }
            });
        } catch (Throwable rejected) {
            restoreInterruptIfInterrupted(rejected);
            result.completeExceptionally(rejected);
        }
        return result;
    }

    /**
     * Records the result of a call that is not executed through a tool, e.g. a call of a tool that does not exist.
     */
    GuardedToolExecution record(ToolExecutionRequest call, ToolExecutionResult result) {
        var execution = unguarded(call, result);
        executions.put(call, execution);
        return execution;
    }

    /**
     * Non-blocking counterpart of {@link #record(ToolExecutionRequest, ToolExecutionResult)}: anything thrown by
     * {@code result} fails the returned future.
     */
    CompletableFuture<ToolExecutionResult> recordAsync(
            ToolExecutionRequest call, Supplier<ToolExecutionResult> result) {
        return track(call, failOnThrow(() -> CompletableFuture.completedFuture(unguarded(call, result.get()))));
    }

    /**
     * Records the execution of an asynchronous call that returned, and returns the future of its result. A failure is
     * recorded at the entry of the round ({@link #startTool(ToolExecutionRequest, Consumer, Consumer, Executor)}).
     */
    private CompletableFuture<ToolExecutionResult> track(
            ToolExecutionRequest call, CompletableFuture<GuardedToolExecution> execution) {
        CompletableFuture<ToolExecutionResult> result = execution.thenApply(guarded -> {
            executions.put(call, guarded);
            return guarded.result();
        });
        propagateCancellation(result, execution);
        return result;
    }

    private boolean isCancelled() {
        return (cancellation != null) && cancellation.isCancelled();
    }

    /**
     * Cancels an execution whose input guardrails are in flight when the round is canceled. Once the tool has started,
     * the execution runs to completion (a tool is never interrupted), so that its result is known and a compensable
     * tool can be rolled back.
     */
    private void cancelBeforeToolStarts(ToolExecutionRequest call, CompletableFuture<?> execution) {
        if (cancellation != null) {
            cancellation.whenComplete((ignored, error) -> {
                if (cancellation.isCancelled() && !startedRequests.containsKey(call)) {
                    execution.cancel(true);
                }
            });
        }
    }

    private static GuardedToolExecution unguarded(ToolExecutionRequest call, ToolExecutionResult result) {
        return GuardedToolExecution.builder().request(call).result(result).build();
    }

    /**
     * Completes a round of the calls of {@code aiMessage} once every call has been executed. See
     * {@link #complete(AiMessage, List, ChatMemory, List)}.
     */
    Outcome complete(AiMessage aiMessage, ChatMemory chatMemory, List<ChatMessage> messages) {
        return complete(aiMessage, aiMessage.toolExecutionRequests(), chatMemory, messages);
    }

    /**
     * Completes the round once every call has been executed: updates the history with the calls as they were
     * executed, and reports them with their results.
     *
     * @param aiMessage the message of the LLM, already in the history
     * @param calls the calls of the round as they were executed, in the order of the LLM
     * @param chatMemory the chat memory holding {@code aiMessage}, or {@code null} without a memory
     * @param messages the messages holding it, without a memory
     */
    public Outcome complete(
            AiMessage aiMessage, List<ToolExecutionRequest> calls, ChatMemory chatMemory, List<ChatMessage> messages) {
        var roundCalls = calls.stream()
                .map(call -> roundCall(
                        call,
                        ensureNotNull(executions.get(call), "execution of %s cannot be null", call)
                                .result()))
                .toList();
        var requests = roundCalls.stream().map(ToolRoundCall::request).toList();
        return new DefaultToolRoundOutcome(
                roundCalls, updateHistory(aiMessage, calls, requests, chatMemory, messages), null);
    }

    /**
     * Non-blocking counterpart of {@link #complete(AiMessage, List, ChatMemory, List)}, for calls started with
     * {@link #startTool(ToolExecutionRequest, Consumer, Consumer, Executor)}: waits until every call has ended, normally or not, and
     * reports them all. A call that failed is reported with an error result carrying the text of its failure, and the
     * first failure, in the order of the calls, is the {@link Outcome#firstError()} of the round. The history is only
     * updated when no call failed (a round that fails ends the invocation).
     */
    public CompletableFuture<Outcome> completeAsync(
            AiMessage aiMessage, List<ToolExecutionRequest> calls, ChatMemory chatMemory, List<ChatMessage> messages) {
        return failOnThrow(() -> settleAsync(calls, messages)
                .thenCompose(outcome -> (outcome.firstError().isPresent())
                        ? CompletableFuture.completedFuture(outcome)
                        : updateHistoryAsync(aiMessage, calls, outcome.requests(), chatMemory, messages)
                                .thenApply(outcome::withMessages)));
    }

    /**
     * Waits until every call has ended, normally or not, and reports them as
     * {@link #completeAsync(AiMessage, List, ChatMemory, List)} does, without touching the history. For a round that
     * is canceled. The {@link Outcome#messages()} are empty.
     */
    public CompletableFuture<Outcome> settleAsync(List<ToolExecutionRequest> calls) {
        return failOnThrow(() -> settleAsync(calls, List.of()).thenApply(Function.<Outcome>identity()));
    }

    private CompletableFuture<DefaultToolRoundOutcome> settleAsync(
            List<ToolExecutionRequest> calls, List<ChatMessage> messages) {
        var callFutures = calls.stream().map(this::future).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(callFutures)
                // wait for every call to end, normally or not
                .handle((ignored, ignoredError) -> settled(calls, messages));
    }

    private DefaultToolRoundOutcome settled(List<ToolExecutionRequest> calls, List<ChatMessage> messages) {
        // A failure recorded for a call that was also executed comes from after the execution (an
        // afterToolExecution callback that threw): the call failed, whatever its result.
        var roundCalls = calls.stream()
                .map(call -> roundCall(
                        call,
                        error(call)
                                .map(error -> ToolExecutionResult.builder()
                                        .isError(true)
                                        .resultText(ToolService.errorText(error))
                                        .build())
                                .or(() -> result(call))
                                .orElse(null)))
                .toList();
        var firstError = calls.stream()
                .map(this::error)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null);
        return new DefaultToolRoundOutcome(roundCalls, messages, firstError);
    }

    private CompletableFuture<ToolExecutionResult> future(ToolExecutionRequest call) {
        return futures.getOrDefault(call, CompletableFuture.completedFuture(null));
    }

    /**
     * A completed round: the calls as they were executed, in the order of the LLM, the result of each call, the
     * messages for the rest of the round, and the first failure of a call, if any. Pass it to the {@link ToolService}
     * methods that take one: they handle the calls by position, so two calls executed with equal requests keep their
     * own results, and what they need besides the results to compensate the round is kept internal.
     *
     * @since 1.25.0
     */
    public sealed interface Outcome permits DefaultToolRoundOutcome {

        /**
         * The calls as they were executed, in the order of the LLM.
         */
        List<ToolExecutionRequest> requests();

        /**
         * The result of each call, by the call as it was executed. Of calls executed with equal requests, only the
         * first one is in the map: use the {@link ToolService} methods that take the outcome to handle every call.
         */
        Map<ToolExecutionRequest, ToolExecutionResult> results();

        /**
         * The messages for the rest of the round.
         */
        List<ChatMessage> messages();

        /**
         * The first failure of a call of the round, in the order of the LLM, if a call failed.
         */
        Optional<Throwable> firstError();
    }

    /**
     * The calls of a completed round, in the order of the LLM, with their results and what their compensation needs.
     */
    static List<ToolRoundCall> callsOf(Outcome outcome) {
        return ((DefaultToolRoundOutcome) outcome).calls();
    }

    /**
     * A call of the round with the result the LLM sees, and what its compensation needs besides it: the result of its
     * tool before the output guardrails, and why it rolls the other tools back if a tool guardrail made it fail.
     */
    private ToolRoundCall roundCall(ToolExecutionRequest call, ToolExecutionResult result) {
        return new ToolRoundCall(
                reportedRequest(call),
                result,
                toolResults.get(call),
                guardrailFailure(call).orElse(null));
    }

    /**
     * Why a call ended as an error result, if a tool guardrail made it one: the stage the guardrail service tells on
     * the execution of a call that returned ({@link GuardedToolExecution#failedStage()}), or the stage of a fatal
     * failure of a guarded call (a {@link ToolInputGuardrailException} or a {@link ToolOutputGuardrailException}). The
     * round does not infer it from what it saw of the call, so a call that failed for another reason (a callback that
     * threw before the guardrails ran, a tool that errored, a cancellation) is not a guardrail failure.
     */
    private Optional<CompensationReason> guardrailFailure(ToolExecutionRequest call) {
        return Optional.ofNullable(executions.get(call))
                .flatMap(GuardedToolExecution::failedStage)
                .or(() -> Optional.ofNullable(errors.get(call))
                        .filter(error -> binding(call.name()).isPresent())
                        .flatMap(ToolRound::fatalStage))
                .map(ToolRound::compensationReason);
    }

    private static Optional<ToolGuardrailStage> fatalStage(Throwable error) {
        Throwable cause = unwrapCompletionException(error);
        if (cause instanceof ToolInputGuardrailException) {
            return Optional.of(ToolGuardrailStage.INPUT);
        }
        if (cause instanceof ToolOutputGuardrailException) {
            return Optional.of(ToolGuardrailStage.OUTPUT);
        }
        return Optional.empty();
    }

    private static CompensationReason compensationReason(ToolGuardrailStage stage) {
        return switch (stage) {
            case INPUT -> CompensationReason.TOOL_INPUT_GUARDRAIL_FAILED;
            case OUTPUT -> CompensationReason.TOOL_OUTPUT_GUARDRAIL_FAILED;
        };
    }

    /**
     * The request to report for a call, also when it failed: the request the call was executed with, as far as it is
     * known. That is the one of its execution if it returned, the one its tool was started with if it failed after
     * that, the one a fatal input failure of a guarded call carries (see {@link ToolInputGuardrailException#arguments()}),
     * otherwise the call of the LLM.
     */
    ToolExecutionRequest reportedRequest(ToolExecutionRequest call) {
        return reportedRequest(call, null);
    }

    /**
     * As {@link #reportedRequest(ToolExecutionRequest)}, for a call that {@code failure} ended before the round
     * recorded it.
     */
    ToolExecutionRequest reportedRequest(ToolExecutionRequest call, Throwable failure) {
        return Optional.ofNullable(executions.get(call))
                .map(GuardedToolExecution::request)
                .or(() -> Optional.ofNullable(startedRequests.get(call)))
                .or(() -> Optional.ofNullable(failure != null ? failure : errors.get(call))
                        .filter(error -> binding(call.name()).isPresent())
                        .flatMap(error -> rejectedRequest(call, error)))
                .orElse(call);
    }

    private static Optional<ToolExecutionRequest> rejectedRequest(ToolExecutionRequest call, Throwable error) {
        return (unwrapCompletionException(error) instanceof ToolInputGuardrailException inputFailure)
                ? inputFailure.arguments().map(arguments -> call.toBuilder().arguments(arguments).build())
                : Optional.empty();
    }

    /**
     * Whether an input guardrail rewrote a call, so the {@link AiMessage} in the history must carry the rewritten one.
     */
    private static boolean isRewritten(List<ToolExecutionRequest> calls, List<ToolExecutionRequest> requests) {
        return !requests.equals(calls);
    }

    private static AiMessage rewrittenMessage(AiMessage aiMessage, List<ToolExecutionRequest> requests) {
        return aiMessage.toBuilder().toolExecutionRequests(requests).build();
    }

    /**
     * Replaces the tool calls of {@code aiMessage}, the last message with these calls in the chat memory (or in
     * {@code messages} without a memory), with the effective requests when an input guardrail rewrote some of them,
     * so the history holds the arguments the tools ran with.
     *
     * @return the messages for the rest of the round: {@code messages}, or a copy holding the rewritten message
     */
    private static List<ChatMessage> updateHistory(
            AiMessage aiMessage,
            List<ToolExecutionRequest> calls,
            List<ToolExecutionRequest> requests,
            ChatMemory chatMemory,
            List<ChatMessage> messages) {
        List<ChatMessage> result = messages;

        if (isRewritten(calls, requests)) {
            var rewrittenMessage = rewrittenMessage(aiMessage, requests);
            if (chatMemory != null) {
                replace(chatMemory.messages(), aiMessage, rewrittenMessage).ifPresent(chatMemory::set);
            } else {
                result = replace(messages, aiMessage, rewrittenMessage).orElse(messages);
            }
        }

        return result;
    }

    /**
     * Non-blocking counterpart of {@link #updateHistory(AiMessage, List, List, ChatMemory, List)}, with the
     * asynchronous methods of the chat memory.
     */
    private static CompletableFuture<List<ChatMessage>> updateHistoryAsync(
            AiMessage aiMessage,
            List<ToolExecutionRequest> calls,
            List<ToolExecutionRequest> requests,
            ChatMemory chatMemory,
            List<ChatMessage> messages) {
        CompletableFuture<List<ChatMessage>> result;

        if (!isRewritten(calls, requests)) {
            result = CompletableFuture.completedFuture(messages);
        } else if (chatMemory != null) {
            var rewrittenMessage = rewrittenMessage(aiMessage, requests);
            result = chatMemory
                    .messagesAsync()
                    .thenCompose(memoryMessages -> replace(memoryMessages, aiMessage, rewrittenMessage)
                            .map(chatMemory::setAsync)
                            .orElseGet(() -> CompletableFuture.completedFuture(null)))
                    .thenApply(ignored -> messages);
        } else {
            result = CompletableFuture.completedFuture(
                    replace(messages, aiMessage, rewrittenMessage(aiMessage, requests))
                            .orElse(messages));
        }

        return result;
    }

    /**
     * A copy of {@code messages} with the last occurrence of {@code message} replaced, if it occurs.
     */
    private static Optional<List<ChatMessage>> replace(
            List<ChatMessage> messages, AiMessage message, AiMessage replacement) {
        return Optional.ofNullable(messages).map(ArrayList::new).flatMap(copy -> {
            var index = copy.lastIndexOf(message);
            Optional<List<ChatMessage>> replaced = Optional.empty();
            if (index >= 0) {
                copy.set(index, replacement);
                replaced = Optional.of(copy);
            }
            return replaced;
        });
    }

    /**
     * The binding of a tool to the guardrail service, which decides what runs for the tool (only the tool, when it has
     * no guardrails). Absent without a context, and for a tool without a binding (no guardrail service).
     */
    private Optional<ToolGuardrailBinding> binding(String toolName) {
        return Optional.ofNullable(toolServiceContext)
                .map(context -> context.toolGuardrailBindings().get(toolName));
    }

    /**
     * The parameters of the tool guardrail requests: the ones of the AI service invocation, so tool guardrails see the
     * same context as the AI service guardrails. When the context carries none (it was not created by an AI service
     * invocation), minimal parameters are used, with the registrar of the invocation if there is one. Without a
     * registrar, the events of the tool guardrails are lost.
     */
    private GuardrailRequestParams guardrailRequestParams() {
        return toolServiceContext.guardrailRequestParams().orElseGet(() -> {
            if (listenerRegistrar == null) {
                log.debug("No listener registrar for the tool guardrails: their events are not delivered to listeners");
            }
            return GuardrailRequestParams.builder()
                    .userMessageTemplate("")
                    .variables(Map.of())
                    .invocationContext(invocationContext)
                    .aiServiceListenerRegistrar(listenerRegistrar)
                    .build();
        });
    }
}
