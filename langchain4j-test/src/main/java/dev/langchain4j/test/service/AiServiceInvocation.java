package dev.langchain4j.test.service;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.RetrievalAugmentedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.test.service.internal.InvocationRecord;
import dev.langchain4j.test.service.internal.ModelCallRecord;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What happened during one AI Service invocation: what it produced, the tools it executed, the sources it retrieved,
 * the calls to models made during it (its own calls to its chat model and the tokens they used, and the calls of the
 * components it runs), how it ended, and the AI Service invocations that its work started (for example its tools,
 * guardrails or content retrievers).
 * <p>
 * Captured with {@link AiServiceCapture} (see {@link AiServiceCapture#invocation()}). Checked with
 * {@code LangChain4jAssertions.assertThat(...)}.
 * <p>
 * The tool assertions mark the tool executions they check, so that
 * {@link AiServiceInvocationAssert#hasNoMoreToolExecutions()} can tell which ones were not checked. These marks are
 * kept in the invocation, so do not share a capture between tests: capture in each test.
 */
@Experimental
public final class AiServiceInvocation {

    private final InvocationContext invocationContext;
    private final Object output;
    private final int requestsIssued;
    private final List<ToolExecution> toolExecutions;
    private final boolean toolFailuresReported;
    private final List<Content> sources;
    private final Throwable error;
    private final boolean cancelled;
    private final List<AiServiceInvocation> nestedInvocations;
    private final List<ModelCall> modelCalls;
    private final Set<ToolExecution> verifiedToolExecutions =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private AiServiceInvocation(InvocationRecord record) {
        Object completedResult = null;
        boolean allToolFailuresReported = true;
        List<ToolExecution> executions = new ArrayList<>();
        List<Content> retrieved = null;
        Throwable failure = null;
        boolean wasCancelled = false;
        List<List<ChatMessage>> ownRequests = new ArrayList<>();
        Set<ChatResponse> ownResponses = Collections.newSetFromMap(new IdentityHashMap<>());
        for (AiServiceEvent event : record.events()) {
            if (event instanceof AiServiceRequestIssuedEvent requestIssued) {
                ownRequests.add(requestIssued.request().messages());
            } else if (event instanceof AiServiceResponseReceivedEvent responseReceived) {
                ownResponses.add(responseReceived.response());
            }
            if (event instanceof ToolExecutedEvent toolExecuted) {
                Boolean failed = reportedFailure(toolExecuted);
                allToolFailuresReported &= failed != null;
                executions.add(ToolExecution.builder()
                        .request(toolExecuted.request())
                        .result(ToolExecutionResult.builder()
                                .resultContents(toolExecuted.resultContents())
                                .isError(Boolean.TRUE.equals(failed))
                                .build())
                        .invocationContext(toolExecuted.invocationContext())
                        .build());
            } else if (event instanceof RetrievalAugmentedEvent retrievalAugmented) {
                retrieved = retrieved == null ? new ArrayList<>() : retrieved;
                retrieved.addAll(retrievalAugmented.contents());
            } else if (event instanceof AiServiceCompletedEvent completed) {
                completedResult = completed.result().orElse(null);
            } else if (event instanceof AiServiceErrorEvent failed) {
                failure = failed.error();
            } else if (event instanceof AiServiceCancelledEvent) {
                wasCancelled = true;
            }
        }
        if (completedResult instanceof Result<?> result) {
            completedResult = result.content();
        } else if (completedResult instanceof ChatResponse chatResponse
                && !AiServiceMethods.returns(record.invocationContext(), ChatResponse.class)) {
            // a streaming method reports its final ChatResponse, with the token usage of all its calls: what it
            // produced is the text
            completedResult = chatResponse.aiMessage().text();
        }
        List<ModelCall> calls = new ArrayList<>();
        for (ModelCallRecord call : record.modelCalls()) {
            // a call is the AI Service's own when it returned a response the AI Service received, or, for a failed
            // call, when the AI Service issued a request with the same messages
            calls.add(modelCall(call, ownRequests, ownResponses));
        }
        this.invocationContext = record.invocationContext();
        this.output = completedResult;
        this.requestsIssued = ownRequests.size();
        this.toolExecutions = List.copyOf(executions);
        this.toolFailuresReported = allToolFailuresReported;
        this.sources = retrieved == null ? null : List.copyOf(retrieved);
        this.error = failure;
        this.cancelled = wasCancelled;
        this.nestedInvocations = record.children().stream().map(AiServiceInvocation::new).toList();
        this.modelCalls = List.copyOf(calls);
    }

    static AiServiceInvocation from(InvocationRecord record) {
        return new AiServiceInvocation(record);
    }

    private static ModelCall modelCall(
            ModelCallRecord call, List<List<ChatMessage>> ownRequests, Set<ChatResponse> ownResponses) {
        ModelProvider provider = call.modelProvider();
        Object response = call.response();
        Throwable error = call.error();
        if (call.request() instanceof ChatRequest request) {
            boolean own = response != null
                    ? ownResponses.contains(response)
                    : ownRequests.contains(request.messages());
            return new ChatModelCall(provider, request, (ChatResponse) response, error, own);
        }
        if (call.request() instanceof EmbeddingRequest request) {
            return new EmbeddingModelCall(provider, request, (EmbeddingResponse) response, error);
        }
        if (call.request() instanceof ScoringRequest request) {
            return new ScoringModelCall(provider, request, (ScoringResponse) response, error);
        }
        if (call.request() instanceof ModerationRequest request) {
            return new ModerationModelCall(provider, request, (ModerationResponse) response, error);
        }
        return new DecisionModelCall(provider, (DecisionRequest) call.request(), (DecisionResponse) response, error);
    }

    private static Boolean reportedFailure(ToolExecutedEvent toolExecuted) {
        try {
            return toolExecuted.isError();
        } catch (UnsupportedOperationException notReported) {
            return null;
        }
    }

    /**
     * Returns the context of the invocation: its id, the AI Service interface and method, the method arguments and
     * more.
     */
    public InvocationContext invocationContext() {
        return invocationContext;
    }

    /**
     * Returns what the AI Service method produced:
     * <ul>
     *     <li>for a method that returns a value directly, that value, for example a {@code String} or the object of a
     *     method with structured output;</li>
     *     <li>for a method that returns a {@code Result}, a {@code CompletableFuture} or another asynchronous value,
     *     the value inside it;</li>
     *     <li>for a streaming method (one that returns a {@code TokenStream} or a publisher such as a {@code Flux}),
     *     the text of the final response. Text that the model streamed in earlier responses, before it called tools, is
     *     not included: to check everything that was streamed, collect it with {@code onPartialResponse(...)}, or see
     *     {@link AiServiceCapture#lambdaOutputItems()} when the lambda returns a publisher.</li>
     * </ul>
     *
     * @throws AssertionError if the invocation failed or was cancelled, or if the AI Service method returns
     *                        {@code void}
     */
    public Object output() {
        if (error != null) {
            throw new AssertionError("The AI Service invocation failed, so it has no output", error);
        }
        if (cancelled) {
            throw new AssertionError("The AI Service invocation was cancelled, so it has no output");
        }
        if (returnsNothing()) {
            throw new AssertionError("The AI Service method %s returns %s, so it produced no output"
                    .formatted(
                            invocationContext.methodName(),
                            AiServiceMethods.returnType(invocationContext).getTypeName()));
        }
        return output;
    }

    boolean returnsNothing() {
        if (output != null) {
            return false;
        }
        Type returnType = AiServiceMethods.returnType(invocationContext);
        return returnType != null && AiServiceMethods.isNothing(returnType);
    }

    /**
     * Returns the text produced by the AI Service method: the answer of a method that returns a {@code String} (also
     * inside a {@link Result} or a {@code CompletableFuture}), or the text of the final response of a streaming method
     * (see {@link #output()}). {@code null} if such a method produced no text, for example a streaming method whose
     * final response has only tool calls.
     *
     * @throws AssertionError if the invocation failed or was cancelled, or if the method does not produce text,
     *                        for example a method with structured output (use {@link #output()} then) or a
     *                        method that returns nothing
     */
    public String outputText() {
        if (error == null && !cancelled && returnsNothing()) {
            throw new AssertionError("The AI Service method returns %s, so it produced no text"
                    .formatted(AiServiceMethods.returnType(invocationContext).getTypeName()));
        }
        Object output = output();
        if (output instanceof String text) {
            return text;
        }
        if (output instanceof ChatResponse chatResponse) {
            return chatResponse.aiMessage().text();
        }
        if (output instanceof AiMessage aiMessage) {
            return aiMessage.text();
        }
        if (output != null) {
            throw new AssertionError("The AI Service method produced a %s, not text: use output() instead"
                    .formatted(output.getClass().getSimpleName()));
        }
        Type returnType = AiServiceMethods.returnType(invocationContext);
        if (returnType != null && !AiServiceMethods.isText(returnType)) {
            throw new AssertionError("The AI Service method returns %s, not text: use output() instead"
                    .formatted(returnType.getTypeName()));
        }
        return null;
    }

    /**
     * Returns the first request that the chat model of the AI Service answered: the request of the first of the
     * {@link #ownChatModelCalls()} that returned a response. It holds the messages the AI Service built for the model
     * (for example the system message, the messages from the chat memory and the user message, with the retrieved
     * contents) and the tools it offered, with the default request parameters of the chat model (for example the
     * model name or the temperature set on the model).
     *
     * @throws AssertionError if the chat model did not answer any request (for example when the invocation failed
     *                        before), or if it did not notify its listeners (see {@link #ownChatModelCalls()})
     */
    public ChatRequest firstRequest() {
        return answeredOwnCalls().get(0).request();
    }

    /**
     * Returns the last request that the chat model of the AI Service answered: the request of the last of the
     * {@link #ownChatModelCalls()} that returned a response. When the AI Service executed tools, it holds the whole
     * conversation that led to the final answer: the messages of the first request, the tool calls of the model and
     * the tool results.
     *
     * @throws AssertionError if the chat model did not answer any request (for example when the invocation failed
     *                        before), or if it did not notify its listeners (see {@link #ownChatModelCalls()})
     */
    public ChatRequest lastRequest() {
        return last(answeredOwnCalls()).request();
    }

    /**
     * Returns the last response of the chat model of the AI Service. When the AI Service executed tools, this is the
     * response after the last tool results, usually the final answer. Its token usage is that of this call only, also
     * for streaming methods; see {@link #tokenUsage()} for all the calls.
     *
     * @throws AssertionError if the chat model did not answer any request (for example when the invocation failed
     *                        before), or if it did not notify its listeners (see {@link #ownChatModelCalls()})
     */
    public ChatResponse lastResponse() {
        return last(answeredOwnCalls()).response();
    }

    private List<ChatModelCall> answeredOwnCalls() {
        List<ChatModelCall> answered = ownChatModelCalls().stream()
                .filter(call -> call.response() != null)
                .toList();
        if (!answered.isEmpty()) {
            return answered;
        }
        if (chatModelNotifiedNoListeners()) {
            throw new AssertionError(notNotifiedMessage());
        }
        throw new AssertionError("The chat model did not respond to the AI Service invocation"
                + (error == null ? "" : ": it failed with " + error));
    }

    private static <T> T last(List<T> list) {
        return list.get(list.size() - 1);
    }

    /**
     * The explanation for an invocation that sent requests to its chat model while the model notified no listeners,
     * so that none of its calls was captured.
     */
    String notNotifiedMessage() {
        return ("The AI Service sent %d request(s) to its chat model, but the chat model did not notify its listeners,"
                        + " so its calls were not captured. This happens when a chat model overrides chat(...) instead"
                        + " of implementing doChat(...).")
                .formatted(requestsIssued);
    }

    boolean chatModelNotifiedNoListeners() {
        return requestsIssued > 0 && ownChatModelCalls().isEmpty();
    }

    /**
     * Returns the executed tools, in the order of execution, also those whose execution failed (see
     * {@link ToolExecution#hasFailed()}). The tool executions of {@linkplain #nestedInvocations() nested invocations}
     * are not included.
     *
     * @throws AssertionError if the AI Service did not report whether its tool executions failed (see
     *                        {@link ToolExecutedEvent#isError()}):
     *                        {@link ToolExecution#hasFailed()} would then be {@code false} for every execution
     */
    public List<ToolExecution> toolExecutions() {
        if (!toolFailuresReported) {
            throw new AssertionError("The AI Service did not report whether its tool executions failed (see "
                    + "ToolExecutedEvent.isError()), so ToolExecution.hasFailed() would not be reliable. The names of "
                    + "the executed tools can be checked with assertThat(...).executedToolNames().");
        }
        return toolExecutions;
    }

    List<ToolExecution> allToolExecutions() {
        return toolExecutions;
    }

    void markVerified(ToolExecution toolExecution) {
        verifiedToolExecutions.add(toolExecution);
    }

    boolean isVerified(ToolExecution toolExecution) {
        return verifiedToolExecutions.contains(toolExecution);
    }

    /**
     * Returns whether the AI Service reported, for every executed tool, whether its execution failed.
     */
    boolean toolFailuresReported() {
        return toolFailuresReported;
    }

    /**
     * Returns the contents retrieved for retrieval-augmented generation (RAG) during this invocation, empty if nothing
     * was retrieved.
     *
     * @throws AssertionError if the AI Service has no retrieval augmentor, or did not report what it retrieved
     */
    public List<Content> sources() {
        if (sources == null) {
            throw new AssertionError("The AI Service invocation reported no retrieved contents: the AI Service "
                    + "has no retrieval augmentor, or it does not report what it retrieves");
        }
        return sources;
    }

    boolean sourcesReported() {
        return sources != null;
    }

    /**
     * Returns the tokens used, summed over the calls that the AI Service itself made to its chat model during the
     * invocation (for example, before and after executing tools). Models called by other components are not included,
     * for example by a query transformer, a query router, a guardrail or an embedding model, and neither are the tokens
     * of {@linkplain #nestedInvocations() nested invocations}. {@code null} if the model did not report them, or if
     * no call of the chat model was captured (see {@link #ownChatModelCalls()}).
     */
    public TokenUsage tokenUsage() {
        TokenUsage sum = null;
        for (ChatModelCall call : ownChatModelCalls()) {
            if (call.response() != null) {
                sum = TokenUsage.sum(sum, call.response().tokenUsage());
            }
        }
        return sum;
    }

    /**
     * Returns why the final response finished, or {@code null} if the model did not report it.
     */
    public FinishReason finishReason() {
        List<ChatResponse> responses = ownChatModelCalls().stream()
                .map(ChatModelCall::response)
                .filter(Objects::nonNull)
                .toList();
        return responses.isEmpty() ? null : last(responses).finishReason();
    }

    /**
     * Returns the error the invocation failed with, or {@code null} if it did not fail.
     */
    public Throwable error() {
        return error;
    }

    /**
     * Returns whether the invocation was cancelled before it completed.
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Returns the calls to models made during this invocation, in the order they started: those of the AI Service to
     * its own chat model (see {@link #ownChatModelCalls()}) and those of the components it runs, for example a query
     * transformer, an embedding model used for retrieval, a scoring model, a moderation model or a judge in a
     * guardrail. The calls of {@linkplain #nestedInvocations() nested invocations} are not included, and neither are
     * the calls of models that bypass the listeners LangChain4j notifies (see {@link ModelCall}).
     */
    public List<ModelCall> modelCalls() {
        return modelCalls;
    }

    /**
     * Returns the calls of the AI Service to its own chat model, in the order they started: for example one call, then
     * one more after each round of tool executions, or after an output guardrail asked the model again. Each request
     * is the one the chat model received, with its default request parameters. Calls that failed are included (see
     * {@link ModelCall#hasFailed()}).
     * <p>
     * They are captured from the listeners of the chat model, so the list is empty for a chat model that does not
     * notify its listeners, for example one that overrides {@code chat(...)} instead of implementing
     * {@code doChat(...)}.
     */
    public List<ChatModelCall> ownChatModelCalls() {
        return chatModelCalls().stream().filter(ChatModelCall::isOwn).toList();
    }

    /**
     * Returns the calls to chat models made during this invocation (see {@link #modelCalls()}), those of the AI
     * Service to its own chat model and those of the components it runs.
     */
    public List<ChatModelCall> chatModelCalls() {
        return callsOf(ChatModelCall.class);
    }

    /**
     * Returns the calls to embedding models made during this invocation (see {@link #modelCalls()}), for example to
     * embed the query of a retrieval.
     */
    public List<EmbeddingModelCall> embeddingModelCalls() {
        return callsOf(EmbeddingModelCall.class);
    }

    /**
     * Returns the calls to scoring models made during this invocation (see {@link #modelCalls()}), for example to
     * re-rank the retrieved contents.
     */
    public List<ScoringModelCall> scoringModelCalls() {
        return callsOf(ScoringModelCall.class);
    }

    /**
     * Returns the calls to moderation models made during this invocation (see {@link #modelCalls()}).
     */
    public List<ModerationModelCall> moderationModelCalls() {
        return callsOf(ModerationModelCall.class);
    }

    /**
     * Returns the calls to decision models made during this invocation (see {@link #modelCalls()}).
     */
    public List<DecisionModelCall> decisionModelCalls() {
        return callsOf(DecisionModelCall.class);
    }

    private <T extends ModelCall> List<T> callsOf(Class<T> type) {
        return modelCalls.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /**
     * Returns the AI Service invocations started by the work of this invocation (for example by its tools,
     * guardrails or content retrievers), in the order they started. An integration that does not report which
     * invocation started another one (see {@link InvocationContext#parentInvocationId()})
     * has no nested invocations: the invocations it starts on the capture thread are captured as separate ones.
     */
    public List<AiServiceInvocation> nestedInvocations() {
        return nestedInvocations;
    }

    @Override
    public String toString() {
        return "AiServiceInvocation{" + "method="
                + invocationContext.interfaceName() + "." + invocationContext.methodName()
                + ", toolExecutions="
                + toolExecutions.size() + ", modelCalls="
                + modelCalls.size() + ", error="
                + error + ", cancelled="
                + cancelled + '}';
    }
}
