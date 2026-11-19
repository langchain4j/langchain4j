package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.CompletableFutureUtils.startSafely;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.ToolGuardrailStage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.CompensationReason;
import dev.langchain4j.service.guardrail.GuardedToolExecution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One round of tool execution: the tool calls of one {@link AiMessage}, in every AI service mode (blocking,
 * {@code CompletableFuture}, {@code TokenStream}, reactive). A round is started by
 * {@link ToolService#startRound(ToolServiceContext, InvocationContext, AiServiceListenerRegistrar)} and is the single
 * way in and out of executing its calls:
 * <ul>
 *     <li><b>in:</b> {@link #executeTool(ToolExecutionRequest, Consumer, Consumer)} (blocking) or
 *     {@link #startTool(ToolExecutionRequest, Consumer, Consumer, Executor)} (non-blocking) executes a call, through
 *     the tool guardrails of its tool if it has any;</li>
 *     <li><b>out:</b> once every call has run, {@link #complete(AiMessage, List, ChatMemory, List)} or
 *     {@link #completeAsync(AiMessage, List, ChatMemory, List)} reports the round: the calls as they were executed,
 *     their results, and the messages for the rest of the round ({@link #settleAsync(List)} for a canceled round,
 *     and {@link #fireEventsOfFailedRound(List)} for a round that fails with an exception).</li>
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
     * records it, for {@link #fireEventsOfFailedRound(List)}; a {@link VirtualMachineError} is rethrown without being
     * recorded.
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
    }

    /**
     * Fires the {@link dev.langchain4j.observability.api.event.ToolExecutedEvent}s of the round when it fails with an
     * exception that ends the invocation, reporting each call with the request it was executed with: the calls that
     * returned with their result, and the calls that threw with their exception.
     *
     * @param calls the calls of the round, as they were started, in the order of the LLM
     */
    public void fireEventsOfFailedRound(List<ToolExecutionRequest> calls) {
        ToolService.fireEventsOfFailedRound(invocationContext, calls, this, errors(calls), listenerRegistrar);
    }

    /**
     * The failure of each call that ended with an exception, in the order of {@code calls}.
     */
    private Map<ToolExecutionRequest, Throwable> errors(List<ToolExecutionRequest> calls) {
        Map<ToolExecutionRequest, Throwable> failures = new LinkedHashMap<>();
        for (ToolExecutionRequest call : calls) {
            Optional.ofNullable(errors.get(call)).ifPresent(error -> failures.putIfAbsent(call, error));
        }
        return failures;
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
                                    binding.key(),
                                    call,
                                    binding.toolSpecification(),
                                    binding.toolSource(),
                                    guardrailRequestParams(),
                                    recordingTool))
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
     * @return the future of the result to send to the LLM
     */
    CompletableFuture<ToolExecutionResult> executeAsync(
            ToolExecutionRequest call,
            Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> tool,
            Function<ToolArgumentsException, CompletableFuture<ToolExecutionResult>> argumentsError) {

        Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> recordingTool = request -> {
            // recorded before checking the cancellation, so that a cancellation either sees the tool as started (and
            // lets it run to completion) or stops it here
            startedRequests.put(call, request);
            CompletableFuture<ToolExecutionResult> recorded;
            if (isCancelled()) {
                recorded = CompletableFuture.failedFuture(new CancellationException());
            } else {
                var toolResult = startSafely(() -> tool.apply(request), "future of the tool");
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
                    .map(binding -> startSafely(
                            () -> binding.service()
                                    .executeGuardrailsAsync(
                                            binding.key(),
                                            call,
                                            binding.toolSpecification(),
                                            binding.toolSource(),
                                            guardrailRequestParams(),
                                            recordingTool),
                            "future of the tool guardrails"))
                    .orElseGet(() -> recordingTool.apply(call).thenApply(result -> unguarded(call, result)));
            var guarded = execution;
            execution = guarded.exceptionallyCompose(error -> argumentsErrorBeforeTool(call, error)
                    .map(argumentsException -> argumentsError
                            .apply(argumentsException)
                            .thenApply(result -> unguarded(call, result)))
                    .orElseGet(() -> CompletableFuture.failedFuture(error)));
            propagateCancellation(execution, guarded);
            cancelBeforeToolStarts(call, execution);
        }
        return track(call, execution);
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
     * Non-blocking counterpart of {@link #record(ToolExecutionRequest, ToolExecutionResult)}: an exception thrown by
     * {@code result} fails the returned future.
     */
    CompletableFuture<ToolExecutionResult> recordAsync(
            ToolExecutionRequest call, Supplier<ToolExecutionResult> result) {
        return track(call, startSafely(() -> CompletableFuture.completedFuture(unguarded(call, result.get()))));
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

    /**
     * The result of the tool of a call before its output guardrails, empty if the tool did not run (an input
     * guardrail rejected the call, or the tool does not exist). The compensation rules compare it with the
     * guarded result.
     */
    Optional<ToolExecutionResult> toolResult(ToolExecutionRequest call) {
        return Optional.ofNullable(toolResults.get(call));
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
        var requests = calls.stream()
                .map(call -> ensureNotNull(executions.get(call), "execution of " + call)
                        .request())
                .toList();
        return new Outcome(
                requests,
                results(calls),
                updateHistory(aiMessage, calls, requests, chatMemory, messages),
                null,
                compensation(calls));
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
        return settleAsync(calls, messages)
                .thenCompose(outcome -> (outcome.firstError().isPresent())
                        ? CompletableFuture.completedFuture(outcome)
                        : updateHistoryAsync(aiMessage, calls, outcome.requests(), chatMemory, messages)
                                .thenApply(outcome::withMessages));
    }

    /**
     * Waits until every call has ended, normally or not, and reports them as
     * {@link #completeAsync(AiMessage, List, ChatMemory, List)} does, without touching the history. For a round that
     * is canceled. The {@link Outcome#messages()} are empty.
     */
    public CompletableFuture<Outcome> settleAsync(List<ToolExecutionRequest> calls) {
        return settleAsync(calls, List.of());
    }

    private CompletableFuture<Outcome> settleAsync(List<ToolExecutionRequest> calls, List<ChatMessage> messages) {
        var callFutures = calls.stream().map(this::future).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(callFutures)
                // wait for every call to end, normally or not
                .handle((ignored, ignoredError) -> settled(calls, messages));
    }

    private Outcome settled(List<ToolExecutionRequest> calls, List<ChatMessage> messages) {
        Map<ToolExecutionRequest, ToolExecutionResult> results = new LinkedHashMap<>();
        Throwable firstError = null;
        for (ToolExecutionRequest call : calls) {
            var execution = executions.get(call);
            var error = errors.get(call);
            // A failure recorded for a call that was also executed comes from after the execution (an
            // afterToolExecution callback that threw): the call failed, whatever its result.
            if (error != null) {
                results.putIfAbsent(
                        reportedRequest(call),
                        ToolExecutionResult.builder()
                                .isError(true)
                                .resultText(ToolService.errorText(error))
                                .build());
                firstError = (firstError != null) ? firstError : error;
            } else if (execution != null) {
                results.putIfAbsent(execution.request(), execution.result());
            }
        }
        var requests = calls.stream().map(this::reportedRequest).toList();
        return new Outcome(requests, results, messages, firstError, compensation(calls));
    }

    private CompletableFuture<ToolExecutionResult> future(ToolExecutionRequest call) {
        return futures.getOrDefault(call, CompletableFuture.completedFuture(null));
    }

    /**
     * A completed round.
     *
     * @param requests the calls as they were executed, in the order of the LLM
     * @param results the result of each call, by the call as it was executed
     * @param messages the messages for the rest of the round
     * @param error the first failure of a call, in the order of the LLM, or {@code null}
     * @param compensation what the compensation of the round needs besides {@code results}
     */
    public record Outcome(
            List<ToolExecutionRequest> requests,
            Map<ToolExecutionRequest, ToolExecutionResult> results,
            List<ChatMessage> messages,
            Throwable error,
            ToolRoundCompensation compensation) {

        /**
         * The first failure of a call of the round, in the order of the LLM, if a call failed.
         */
        public Optional<Throwable> firstError() {
            return Optional.ofNullable(error);
        }

        private Outcome withMessages(List<ChatMessage> messages) {
            return new Outcome(requests, results, messages, error, compensation);
        }
    }

    /**
     * The guarded result of each executed call, by the call as the input guardrails left it, in the order of
     * {@code calls}.
     */
    Map<ToolExecutionRequest, ToolExecutionResult> results(List<ToolExecutionRequest> calls) {
        Map<ToolExecutionRequest, ToolExecutionResult> results = new LinkedHashMap<>();
        for (ToolExecutionRequest call : calls) {
            Optional.ofNullable(executions.get(call))
                    .ifPresent(execution -> results.putIfAbsent(execution.request(), execution.result()));
        }
        return results;
    }

    /**
     * What the compensation of the round needs besides the results the LLM sees, for {@code calls}: the result of each
     * tool before its output guardrails, and the calls that a tool guardrail turned into an error result.
     */
    private ToolRoundCompensation compensation(List<ToolExecutionRequest> calls) {
        var originalResults = new LinkedHashMap<ToolExecutionRequest, ToolExecutionResult>();
        var guardrailFailures = new LinkedHashMap<ToolExecutionRequest, CompensationReason>();

        calls.forEach(call -> {
            var executed = reportedRequest(call);
            var original = toolResults.get(call);

            Optional.ofNullable(original).ifPresent(result -> originalResults.putIfAbsent(executed, result));

            guardrailFailure(call).ifPresent(reason -> guardrailFailures.putIfAbsent(executed, reason));
        });

        return new ToolRoundCompensation(originalResults, guardrailFailures);
    }

    /**
     * Why a call ended as an error result, if a tool guardrail made it one. The guardrail service tells the stage: on
     * the execution of a call that returned ({@link GuardedToolExecution#failedStage()}), or for a fatal failure
     * ({@code ToolGuardrailService#rejectedStage}). The round does not infer it from what it saw of the call, so a
     * call that failed for another reason (a callback that threw before the guardrails ran, a tool that errored, a
     * cancellation) is not a guardrail failure.
     */
    private Optional<CompensationReason> guardrailFailure(ToolExecutionRequest call) {
        return Optional.ofNullable(executions.get(call))
                .flatMap(GuardedToolExecution::failedStage)
                .or(() -> Optional.ofNullable(errors.get(call))
                        .flatMap(error -> binding(call.name())
                                .flatMap(binding -> binding.service().rejectedStage(call, error))))
                .map(ToolRound::compensationReason);
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
     * that, the one the guardrail service tells for a failure that ended the call before (see
     * {@link dev.langchain4j.service.guardrail.ToolGuardrailService#rejectedRequest(ToolExecutionRequest,
     * Throwable)}), otherwise the call of the LLM.
     */
    ToolExecutionRequest reportedRequest(ToolExecutionRequest call) {
        return Optional.ofNullable(executions.get(call))
                .map(GuardedToolExecution::request)
                .or(() -> Optional.ofNullable(startedRequests.get(call)))
                .or(() -> Optional.ofNullable(errors.get(call))
                        .flatMap(error -> binding(call.name())
                                .flatMap(binding -> binding.service().rejectedRequest(call, error))))
                .orElse(call);
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
