package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.ToolInputGuardrailException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.service.guardrail.GuardedToolExecution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One round of the tool loop of {@link ToolService}: the tool calls of one {@link AiMessage}. Each call is
 * {@linkplain #execute(ToolExecutionRequest, Function) executed} through its tool guardrails, if it has any
 * ({@link dev.langchain4j.service.guardrail.ToolGuardrailService#executeGuardrails}), and once every call has run,
 * {@link #complete(ChatMemory, List)} reports the round: the calls as they were executed, their results, and the
 * messages for the rest of the round.
 * <p>
 *     The loop does not need to know that the calls it gets back can differ from the ones of the LLM: an input
 *     guardrail can rewrite the arguments of a call, and the rewritten call is then what everything downstream
 *     carries (D18), including the tool call stored in the history, which {@link #complete(ChatMemory, List)}
 *     updates.
 * </p>
 * <p>
 *     The tools of a round may run concurrently, so {@link #execute(ToolExecutionRequest, Function)} and
 *     {@link #record(ToolExecutionRequest, ToolExecutionResult)} are thread-safe.
 * </p>
 */
@Internal
final class ToolRound {

    private static final Logger log = LoggerFactory.getLogger(ToolRound.class);

    private final AiMessage aiMessage;
    private final List<ToolExecutionRequest> requestedCalls;
    private final ToolServiceContext toolServiceContext;
    private final InvocationContext invocationContext;
    private final AiServiceListenerRegistrar listenerRegistrar;
    private final Map<ToolExecutionRequest, GuardedToolExecution> executions = new ConcurrentHashMap<>();
    private final Map<ToolExecutionRequest, ToolExecutionRequest> startedRequests = new ConcurrentHashMap<>();
    private final Map<ToolExecutionRequest, ToolExecutionResult> toolResults = new ConcurrentHashMap<>();

    private ToolRound(
            AiMessage aiMessage,
            List<ToolExecutionRequest> requestedCalls,
            ToolServiceContext toolServiceContext,
            InvocationContext invocationContext,
            AiServiceListenerRegistrar listenerRegistrar) {
        this.aiMessage = aiMessage;
        this.requestedCalls = List.copyOf(requestedCalls);
        this.toolServiceContext = toolServiceContext;
        this.invocationContext = invocationContext;
        this.listenerRegistrar = listenerRegistrar;
    }

    /**
     * Starts the round of the tool calls of {@code aiMessage}, guarded by the bindings of the given context.
     *
     * @param aiMessage the message of the LLM, already added to the history
     * @param listenerRegistrar the registrar of the invocation, for the events of the tool guardrails when the context
     *                          carries no {@link GuardrailRequestParams}; may be {@code null}
     */
    static ToolRound start(
            AiMessage aiMessage,
            ToolServiceContext toolServiceContext,
            InvocationContext invocationContext,
            AiServiceListenerRegistrar listenerRegistrar) {
        return new ToolRound(
                aiMessage,
                aiMessage.toolExecutionRequests(),
                ensureNotNull(toolServiceContext, "toolServiceContext"),
                invocationContext,
                listenerRegistrar);
    }

    /**
     * Starts a round of one call that no tool guardrail applies to, for callers that have no guardrail bindings and
     * that only {@linkplain #execute(ToolExecutionRequest, Function) execute} the call (they do not
     * {@linkplain #complete(ChatMemory, List) complete} the round).
     */
    static ToolRound unguarded(ToolExecutionRequest call, InvocationContext invocationContext) {
        return new ToolRound(null, List.of(call), null, invocationContext, null);
    }

    /**
     * Executes a call of a tool that exists, through its guardrails if it has any: the input guardrails, then
     * {@code tool}, then the output guardrails. A fatal guardrail failure is thrown from here, outside {@code tool}.
     *
     * @param call the call of the LLM
     * @param tool executes the tool for a request (the call, or the call as the input guardrails rewrote it),
     *             handling its errors
     */
    GuardedToolExecution execute(ToolExecutionRequest call, Function<ToolExecutionRequest, ToolExecutionResult> tool) {
        Function<ToolExecutionRequest, ToolExecutionResult> recordingTool = request -> {
            startedRequests.put(call, request);
            var toolResult = tool.apply(request);
            toolResults.put(call, toolResult);
            return toolResult;
        };

        var execution = binding(call.name())
                .map(binding -> binding.service()
                        .executeGuardrails(
                                binding.key(),
                                call,
                                toolSpecification(call.name()),
                                binding.toolSource(),
                                guardrailRequestParams(),
                                recordingTool))
                .orElseGet(() -> unguarded(call, recordingTool.apply(call)));

        executions.put(call, execution);
        return execution;
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
     * Completes the round once every call has been executed: updates the history with the calls as they were
     * executed, and reports them with their results.
     *
     * @param chatMemory the chat memory holding the {@link AiMessage} of the round, or {@code null} without a memory
     * @param messages the messages holding it, without a memory
     */
    Outcome complete(ChatMemory chatMemory, List<ChatMessage> messages) {
        return new Outcome(requests(), results(), updateHistory(chatMemory, messages));
    }

    /**
     * A completed round.
     *
     * @param requests the calls as they were executed, in the order of the LLM
     * @param results the result of each call, by the call as it was executed
     * @param messages the messages for the rest of the round
     */
    record Outcome(
            List<ToolExecutionRequest> requests,
            Map<ToolExecutionRequest, ToolExecutionResult> results,
            List<ChatMessage> messages) {}

    /**
     * The calls of the round as the input guardrails left them, in the order of the LLM. Every call must have been
     * executed.
     */
    private List<ToolExecutionRequest> requests() {
        return requestedCalls.stream()
                .map(call -> ensureNotNull(executions.get(call), "execution of " + call)
                        .request())
                .toList();
    }

    /**
     * The guarded result of each executed call, by the call as the input guardrails left it, in the order of the LLM.
     */
    Map<ToolExecutionRequest, ToolExecutionResult> results() {
        Map<ToolExecutionRequest, ToolExecutionResult> results = new LinkedHashMap<>();
        for (ToolExecutionRequest call : requestedCalls) {
            Optional.ofNullable(executions.get(call))
                    .ifPresent(execution -> results.putIfAbsent(execution.request(), execution.result()));
        }
        return results;
    }

    /**
     * The request to report for a call of a round that failed with an exception: the call as the input guardrails
     * left it, as far as it is known. That is the one of its execution if it returned, the one its tool was started
     * with if the tool (or a fatal output guardrail) threw, the arguments a fatal input guardrail validated, otherwise
     * the call of the LLM.
     *
     * @param error the exception of the call, or {@code null} if it has none
     */
    ToolExecutionRequest reportedRequest(ToolExecutionRequest call, Throwable error) {
        return Optional.ofNullable(executions.get(call))
                .map(GuardedToolExecution::request)
                .or(() -> Optional.ofNullable(startedRequests.get(call)))
                .or(() -> Optional.ofNullable(error)
                        .filter(ToolInputGuardrailException.class::isInstance)
                        .map(ToolInputGuardrailException.class::cast)
                        .flatMap(ToolInputGuardrailException::arguments)
                        .map(arguments -> call.toBuilder().arguments(arguments).build()))
                .orElse(call);
    }

    /**
     * Replaces the tool calls of {@code aiMessage}, the last message with these calls in the chat memory (or in
     * {@code messages} without a memory), with the {@linkplain #requests() effective requests} when an input
     * guardrail rewrote some of them, so the history holds the arguments the tools ran with.
     *
     * @return the messages for the rest of the round: {@code messages}, or a copy holding the rewritten message
     */
    private List<ChatMessage> updateHistory(ChatMemory chatMemory, List<ChatMessage> messages) {
        var effectiveRequests = requests();
        List<ChatMessage> result = messages;

        if (!effectiveRequests.equals(requestedCalls)) {
            var rewrittenMessage = aiMessage.toBuilder()
                    .toolExecutionRequests(effectiveRequests)
                    .build();

            if (chatMemory != null) {
                var memoryMessages = new ArrayList<>(chatMemory.messages());
                var index = memoryMessages.lastIndexOf(aiMessage);
                if (index >= 0) {
                    memoryMessages.set(index, rewrittenMessage);
                    chatMemory.set(memoryMessages);
                }
            } else {
                var index = messages.lastIndexOf(aiMessage);
                if (index >= 0) {
                    result = new ArrayList<>(messages);
                    result.set(index, rewrittenMessage);
                }
            }
        }

        return result;
    }

    /**
     * The binding of a tool with guardrails to execute. Absent without a context, for a tool without a binding, and
     * for a bound tool without guardrails (the fast path: not even the guardrail parameters are built).
     */
    private Optional<ToolGuardrailBinding> binding(String toolName) {
        return Optional.ofNullable(toolServiceContext)
                .map(context -> context.toolGuardrailBindings().get(toolName))
                .filter(binding -> binding.hasInputGuardrails() || binding.hasOutputGuardrails());
    }

    private ToolSpecification toolSpecification(String toolName) {
        return toolServiceContext.availableTools().stream()
                .filter(specification -> toolName.equals(specification.name()))
                .findFirst()
                .orElse(null);
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
