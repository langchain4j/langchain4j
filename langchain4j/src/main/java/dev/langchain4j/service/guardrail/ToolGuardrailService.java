package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.CompletableFutureUtils.startSafely;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailException;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import dev.langchain4j.internal.Exceptions;
import dev.langchain4j.service.guardrail.spi.ToolGuardrailServiceBuilderFactory;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Executes the tool guardrails of the tools of an AI service: the {@link ToolInputGuardrail}s, which validate or
 * rewrite the arguments of a tool call before the tool runs, and the {@link ToolOutputGuardrail}s, which validate or
 * rewrite its result before the LLM sees it. It is the tool counterpart of {@link GuardrailService}, and its
 * {@link #targetClass()} is the AI service class too.
 * <p>
 *     <strong>Keys.</strong> The key of a tool is the {@link ToolKey} that {@link #bind(Class, AiServiceTool)}
 *     returns for it: it is only meaningful to the service that created it. A tool provided anew for every
 *     invocation (by a {@link dev.langchain4j.service.tool.ToolProvider}, for example an MCP server) is bound for every
 *     invocation.
 * </p>
 * <p>
 *     <strong>Which guardrails run.</strong> Every tool guardrail that applies to a tool runs, as one chain, in this
 *     order, separately for input and output:
 * </p>
 * <ol>
 *     <li>the guardrails given to the builder (set on the {@code AiServices} builder; they apply to every tool)</li>
 *     <li>the guardrails of the {@link ToolInputGuardrails} / {@link ToolOutputGuardrails} annotation of the class of
 *     the tool</li>
 *     <li>the guardrails of the annotation of the tool method, then the guardrails of the {@link AiServiceTool}</li>
 * </ol>
 * <p>
 *     No level replaces another. This deliberately differs from the AI service guardrails of {@link GuardrailService},
 *     where the builder replaces the annotations. A guardrail that applies to every tool can skip some tools by looking
 *     at {@link ToolInputGuardrailRequest#toolName()} or {@link ToolInputGuardrailRequest#toolSource()}.
 * </p>
 * <p>
 *     A guardrail class that is given at several levels (by annotations or {@code *GuardrailClasses(..)}) runs once,
 *     at its first position. Guardrail instances only count as duplicates of themselves (the same object twice), since
 *     two instances of one class can be configured differently, and an instance and a class naming the same class
 *     both run.
 * </p>
 * <p>
 *     <strong>Results.</strong> A non-fatal failure is returned as a failed result, never thrown, so that the caller
 *     can send it to the LLM as an error tool result. A fatal failure throws a
 *     {@link dev.langchain4j.guardrail.ToolInputGuardrailException} or a
 *     {@link dev.langchain4j.guardrail.ToolOutputGuardrailException} (the asynchronous methods fail their future
 *     with it). {@link #executeGuardrails(ToolKey, ToolExecutionRequest, ToolSpecification, ToolSource,
 *     GuardrailRequestParams, Function)} runs a whole tool call through the guardrails and applies these rules, the
 *     way {@link GuardrailService#executeGuardrails(Object, dev.langchain4j.guardrail.InputGuardrailRequest)} does for
 *     the AI service guardrails; {@link #executeGuardrailsAsync(ToolKey, ToolExecutionRequest, ToolSpecification,
 *     ToolSource, GuardrailRequestParams, Function)} is its non-blocking counterpart, for which the guardrails must
 *     implement {@link dev.langchain4j.guardrail.Guardrail#validateAsync(dev.langchain4j.guardrail.GuardrailRequest)}.
 * </p>
 * <p>
 *     <strong>Customizing.</strong> The default implementation finds the annotations through
 *     {@link dev.langchain4j.spi.classloading.ClassMetadataProviderFactory} and creates guardrails from their classes
 *     through {@link dev.langchain4j.spi.classloading.ClassInstanceFactory}, like the AI service guardrails. A
 *     framework can also replace the whole builder with a {@link ToolGuardrailServiceBuilderFactory}.
 * </p>
 *
 * @since 1.24.0
 */
@Experimental
public non-sealed interface ToolGuardrailService
        extends GenericGuardrailService<
                ToolInputGuardrailRequest,
                ToolInputGuardrailResult,
                ToolOutputGuardrailRequest,
                ToolOutputGuardrailResult> {

    /**
     * Binds a tool to this service: resolves the tool guardrails that apply to the tool, and returns the key to run
     * them with.
     *
     * @param toolClass The class whose {@link ToolInputGuardrails} / {@link ToolOutputGuardrails} annotations apply to
     *                  the tool: the class of the object the {@code @Tool} method of the tool was found on (the object
     *                  given to {@code AiServices.tools(..)}). {@code null} for a tool that was not found on an object
     *                  (an {@link AiServiceTool} given directly, or provided by a
     *                  {@link dev.langchain4j.service.tool.ToolProvider} such as an MCP server); the declaring class of
     *                  the tool method is then used, if the tool has one.
     * @param tool      The tool. Must not be null.
     * @return The {@link ToolKey} that identifies this tool to this service: pass it to
     *         {@link #executeInputGuardrails(Object, dev.langchain4j.guardrail.GuardrailRequest)},
     *         {@link #executeOutputGuardrails(Object, dev.langchain4j.guardrail.GuardrailRequest)}, their asynchronous
     *         variants, and {@link #hasInputGuardrails(Object)} / {@link #hasOutputGuardrails(Object)}. It is only
     *         meaningful to the service that returned it, and never {@code null}. A tool without guardrails also
     *         gets a key, and running its guardrails returns a success.
     */
    ToolKey bind(Class<?> toolClass, AiServiceTool tool);

    /**
     * Executes a tool call through the tool guardrails of the tool: the input guardrails, then {@code tool}, then the
     * output guardrails. It is the tool counterpart of {@link GuardrailService#executeGuardrails(Object,
     * dev.langchain4j.guardrail.InputGuardrailRequest)}: it builds the guardrail requests and turns the guardrail
     * results into the request the tool runs with and the result the LLM sees, so a caller needs nothing else.
     * <ul>
     *     <li>Without guardrails for the tool, {@code tool} runs with {@code request}, and its result is returned as
     *     is.</li>
     *     <li>An input guardrail that rewrites the arguments changes the request that {@code tool} runs with, and
     *     {@link GuardedToolExecution#request()}.</li>
     *     <li>A non-fatal input failure does not run {@code tool}: the result is an error result whose text is the
     *     failure message, verbatim, and the request carries the arguments as the guardrails that ran before the
     *     failure left them.</li>
     *     <li>The output guardrails also run on an error result of {@code tool}. A rewrite replaces the contents and
     *     the error flag of the result, keeps its attributes, and drops its raw {@link ToolExecutionResult#result()},
     *     which no longer matches what the LLM sees. A non-fatal output failure replaces the result with an error
     *     result whose text is the failure message, verbatim.</li>
     *     <li>A fatal failure throws a {@link ToolInputGuardrailException} or a
     *     {@link dev.langchain4j.guardrail.ToolOutputGuardrailException}. It is thrown outside {@code tool}, so the
     *     error handling of the tool does not see it. An exception thrown by {@code tool} propagates as is. When a
     *     fatal input failure ends the call, {@link #rejectedRequest(ToolExecutionRequest, Throwable)} gives the
     *     request as the guardrails before it left it.</li>
     * </ul>
     *
     * @param key               The key {@link #bind(Class, AiServiceTool)} returned for the tool. Must not be null.
     * @param request           The tool call of the LLM. Must not be null.
     * @param toolSpecification The specification of the tool, for the guardrail requests; may be null.
     * @param toolSource        Where the tool comes from, for the guardrail requests; {@code null} means
     *                          {@link ToolSource#programmatic()}.
     * @param requestParams     The parameters of the guardrail requests: those of the AI service invocation, so the
     *                          tool guardrails see the same context as the AI service guardrails and their events
     *                          reach the listeners of the invocation. Must not be null.
     * @param tool              Executes the tool for the request it is given (the call, or the call as the input
     *                          guardrails rewrote it) and returns its result. Must not be null.
     * @return The request as the input guardrails left it, and the result to send to the LLM.
     */
    default GuardedToolExecution executeGuardrails(
            ToolKey key,
            ToolExecutionRequest request,
            ToolSpecification toolSpecification,
            ToolSource toolSource,
            GuardrailRequestParams requestParams,
            Function<ToolExecutionRequest, ToolExecutionResult> tool) {

        ensureNotNull(key, "key");
        ensureNotNull(request, "request");
        ensureNotNull(requestParams, "requestParams");
        ensureNotNull(tool, "tool");

        // without guardrails, nothing but the tool runs: not even a guardrail request is built
        return hasGuardrails(key)
                ? guard(key, request, toolSpecification, toolSource, requestParams, tool)
                : guardedExecution(request, tool.apply(request));
    }

    private GuardedToolExecution guard(
            ToolKey key,
            ToolExecutionRequest request,
            ToolSpecification toolSpecification,
            ToolSource toolSource,
            GuardrailRequestParams requestParams,
            Function<ToolExecutionRequest, ToolExecutionResult> tool) {

        var inputResult = hasInputGuardrails(key)
                ? executeInputGuardrails(key, inputRequest(request, toolSpecification, toolSource, requestParams))
                : ToolInputGuardrailResult.success();

        var effectiveRequest = effectiveRequest(inputResult, request);

        var result = inputResult.isSuccess()
                ? applyOutputGuardrails(
                        key,
                        effectiveRequest,
                        tool.apply(effectiveRequest),
                        toolSpecification,
                        toolSource,
                        requestParams)
                : errorResult(inputResult.failureMessage());

        return guardedExecution(effectiveRequest, result);
    }

    /**
     * Non-blocking counterpart of {@link #executeGuardrails(ToolKey, ToolExecutionRequest, ToolSpecification,
     * ToolSource, GuardrailRequestParams, Function)}, with the same rules, for the asynchronous
     * ({@code CompletableFuture}) and streaming AI service modes. The guardrails run with
     * {@link #executeInputGuardrailsAsync(Object, dev.langchain4j.guardrail.GuardrailRequest)} and
     * {@link #executeOutputGuardrailsAsync(Object, dev.langchain4j.guardrail.GuardrailRequest)}, so a guardrail that
     * overrides {@link dev.langchain4j.guardrail.Guardrail#validateAsync(dev.langchain4j.guardrail.GuardrailRequest)}
     * does not block the calling thread.
     * <p>
     *     Nothing is thrown from this method: a fatal guardrail failure, an exception thrown by a guardrail or by
     *     {@code tool}, and a failed future of {@code tool} all fail the returned future. Canceling the returned future
     *     cancels the guardrails or the tool in flight.
     * </p>
     *
     * @param key               The key {@link #bind(Class, AiServiceTool)} returned for the tool. Must not be null.
     * @param request           The tool call of the LLM. Must not be null.
     * @param toolSpecification The specification of the tool, for the guardrail requests; may be null.
     * @param toolSource        Where the tool comes from, for the guardrail requests; {@code null} means
     *                          {@link ToolSource#programmatic()}.
     * @param requestParams     The parameters of the guardrail requests: those of the AI service invocation. Must not
     *                          be null.
     * @param tool              Starts the tool for the request it is given (the call, or the call as the input
     *                          guardrails rewrote it) and returns the future of its result. Must not be null.
     * @return A future of the request as the input guardrails left it and of the result to send to the LLM.
     * @since 1.24.0
     */
    @Experimental
    default CompletableFuture<GuardedToolExecution> executeGuardrailsAsync(
            ToolKey key,
            ToolExecutionRequest request,
            ToolSpecification toolSpecification,
            ToolSource toolSource,
            GuardrailRequestParams requestParams,
            Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> tool) {

        ensureNotNull(key, "key");
        ensureNotNull(request, "request");
        ensureNotNull(requestParams, "requestParams");
        ensureNotNull(tool, "tool");

        // without guardrails, nothing but the tool runs: not even a guardrail request is built
        return hasGuardrails(key)
                ? guardAsync(key, request, toolSpecification, toolSource, requestParams, tool)
                : executeWithoutGuardrailsAsync(request, tool);
    }

    /**
     * The request of a call that a tool guardrail failure ended, as the guardrails left it, when the failure tells:
     * after a fatal input failure, the call with the arguments the failing guardrail validated (as the guardrails
     * before it rewrote them). It lets a caller report a call that {@link #executeGuardrails(ToolKey,
     * ToolExecutionRequest, ToolSpecification, ToolSource, GuardrailRequestParams, Function)} or
     * {@link #executeGuardrailsAsync(ToolKey, ToolExecutionRequest, ToolSpecification, ToolSource,
     * GuardrailRequestParams, Function)} ended with an exception without knowing the exceptions of the guardrails.
     * <p>
     *     Empty for any other failure: a failure of the tool or of an output guardrail ends a call that the tool was
     *     started with, and the caller knows that request from its {@code tool} function.
     * </p>
     *
     * @param request The tool call of the LLM. Must not be null.
     * @param failure The exception that ended the call, possibly wrapped in a
     *                {@link java.util.concurrent.CompletionException}; may be null.
     * @return The request as the guardrails left it, if the failure tells.
     * @since 1.24.0
     */
    @Experimental
    default Optional<ToolExecutionRequest> rejectedRequest(ToolExecutionRequest request, Throwable failure) {
        ensureNotNull(request, "request");
        return Optional.ofNullable(failure)
                .map(Exceptions::unwrapCompletionException)
                .filter(ToolInputGuardrailException.class::isInstance)
                .map(ToolInputGuardrailException.class::cast)
                .flatMap(ToolInputGuardrailException::arguments)
                .map(arguments -> request.toBuilder().arguments(arguments).build());
    }

    private boolean hasGuardrails(ToolKey key) {
        return hasInputGuardrails(key) || hasOutputGuardrails(key);
    }

    private static CompletableFuture<GuardedToolExecution> executeWithoutGuardrailsAsync(
            ToolExecutionRequest request, Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> tool) {
        var toolResult = startSafely(() -> tool.apply(request), "future of the tool");
        var execution = toolResult.thenApply(result -> guardedExecution(request, result));
        propagateCancellation(execution, toolResult);
        return execution;
    }

    private CompletableFuture<GuardedToolExecution> guardAsync(
            ToolKey key,
            ToolExecutionRequest request,
            ToolSpecification toolSpecification,
            ToolSource toolSource,
            GuardrailRequestParams requestParams,
            Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> tool) {
        var result = new CompletableFuture<GuardedToolExecution>();
        var inFlight = new AtomicReference<CompletableFuture<?>>();

        // canceling the returned future cancels whatever stage is running
        result.whenComplete((ignored, error) -> {
            if (result.isCancelled()) {
                Optional.ofNullable(inFlight.get()).ifPresent(stage -> stage.cancel(true));
            }
        });

        var inputStage = hasInputGuardrails(key)
                ? startStage(
                        inFlight,
                        () -> executeInputGuardrailsAsync(
                                key, inputRequest(request, toolSpecification, toolSource, requestParams)),
                        "future of the tool input guardrails")
                : CompletableFuture.completedFuture(ToolInputGuardrailResult.success());

        inputStage
                .thenCompose(inputResult -> {
                    var effectiveRequest = effectiveRequest(inputResult, request);
                    return inputResult.isSuccess()
                            ? startStage(inFlight, () -> tool.apply(effectiveRequest), "future of the tool")
                                    .thenCompose(toolResult -> applyOutputGuardrailsAsync(
                                            inFlight,
                                            key,
                                            effectiveRequest,
                                            toolResult,
                                            toolSpecification,
                                            toolSource,
                                            requestParams))
                                    .thenApply(guardedResult -> guardedExecution(effectiveRequest, guardedResult))
                            : CompletableFuture.completedFuture(
                                    guardedExecution(effectiveRequest, errorResult(inputResult.failureMessage())));
                })
                .whenComplete((execution, error) -> {
                    if (error != null) {
                        result.completeExceptionally(unwrapCompletionException(error));
                    } else {
                        result.complete(execution);
                    }
                });

        return result;
    }

    /**
     * Starts a stage of {@link #executeGuardrailsAsync}, turning an exception thrown while starting it into a failed
     * future, and makes it the stage that a cancellation cancels.
     */
    private static <T> CompletableFuture<T> startStage(
            AtomicReference<CompletableFuture<?>> inFlight, Supplier<CompletableFuture<T>> stage, String name) {
        var future = startSafely(stage, name);
        inFlight.set(future);
        return future;
    }

    private CompletableFuture<ToolExecutionResult> applyOutputGuardrailsAsync(
            AtomicReference<CompletableFuture<?>> inFlight,
            ToolKey key,
            ToolExecutionRequest request,
            ToolExecutionResult toolResult,
            ToolSpecification toolSpecification,
            ToolSource toolSource,
            GuardrailRequestParams requestParams) {

        CompletableFuture<ToolExecutionResult> guarded;

        if (hasOutputGuardrails(key)) {
            var guardrailRequest = outputRequest(request, toolResult, toolSpecification, toolSource, requestParams);
            guarded = startStage(
                            inFlight,
                            () -> executeOutputGuardrailsAsync(key, guardrailRequest),
                            "future of the tool output guardrails")
                    .thenApply(outputResult -> guardedResult(outputResult, guardrailRequest, toolResult));
        } else {
            guarded = CompletableFuture.completedFuture(toolResult);
        }
        return guarded;
    }

    private ToolExecutionResult applyOutputGuardrails(
            ToolKey key,
            ToolExecutionRequest request,
            ToolExecutionResult toolResult,
            ToolSpecification toolSpecification,
            ToolSource toolSource,
            GuardrailRequestParams requestParams) {
        return hasOutputGuardrails(key)
                ? guardToolResult(key, request, toolResult, toolSpecification, toolSource, requestParams)
                : toolResult;
    }

    private ToolExecutionResult guardToolResult(
            ToolKey key,
            ToolExecutionRequest request,
            ToolExecutionResult toolResult,
            ToolSpecification toolSpecification,
            ToolSource toolSource,
            GuardrailRequestParams requestParams) {

        var guardrailRequest = outputRequest(request, toolResult, toolSpecification, toolSource, requestParams);

        return guardedResult(executeOutputGuardrails(key, guardrailRequest), guardrailRequest, toolResult);
    }

    private static ToolInputGuardrailRequest inputRequest(
            ToolExecutionRequest request,
            ToolSpecification toolSpecification,
            ToolSource toolSource,
            GuardrailRequestParams requestParams) {

        return ToolInputGuardrailRequest.builder()
                .toolExecutionRequest(request)
                .toolSpecification(toolSpecification)
                .toolSource(toolSource)
                .requestParams(requestParams)
                .build();
    }

    private static ToolOutputGuardrailRequest outputRequest(
            ToolExecutionRequest request,
            ToolExecutionResult toolResult,
            ToolSpecification toolSpecification,
            ToolSource toolSource,
            GuardrailRequestParams requestParams) {

        return ToolOutputGuardrailRequest.builder()
                .toolExecutionRequest(request)
                .toolSpecification(toolSpecification)
                .toolSource(toolSource)
                .requestParams(requestParams)
                .resultContents(toolResult.resultContents())
                .isError(toolResult.isError())
                .build();
    }

    /**
     * The request as the input guardrails left it. A rejected call is reported with the arguments as the guardrails
     * before the rejection left them.
     */
    private static ToolExecutionRequest effectiveRequest(
            ToolInputGuardrailResult inputResult, ToolExecutionRequest request) {

        return inputResult
                .rewrittenArguments()
                .map(arguments -> request.toBuilder().arguments(arguments).build())
                .orElse(request);
    }

    /**
     * The result the LLM sees after the output guardrails. The raw {@code result()} is dropped on a rewrite: it no
     * longer matches what the LLM sees. The attributes are kept.
     */
    private static ToolExecutionResult guardedResult(
            ToolOutputGuardrailResult outputResult,
            ToolOutputGuardrailRequest guardrailRequest,
            ToolExecutionResult toolResult) {

        return outputResult.isSuccess()
                ? (outputResult.hasRewrittenResult()
                        ? ToolExecutionResult.builder()
                                .resultContents(outputResult.resultContents(guardrailRequest))
                                .isError(outputResult.isError(guardrailRequest))
                                .attributes(toolResult.attributes())
                                .build()
                        : toolResult)
                : errorResult(outputResult.failureMessage());
    }

    private static GuardedToolExecution guardedExecution(ToolExecutionRequest request, ToolExecutionResult result) {
        return GuardedToolExecution.builder().request(request).result(result).build();
    }

    /**
     * The error result of a guardrail failure: the failure message, verbatim.
     */
    private static ToolExecutionResult errorResult(String message) {
        return ToolExecutionResult.builder().isError(true).resultText(message).build();
    }

    /**
     * Creates a builder of {@link ToolGuardrailService}. If a {@link ToolGuardrailServiceBuilderFactory} is found by
     * {@link ServiceLoader}, the first one found provides the builder; otherwise the default builder is used.
     *
     * @param aiServiceClass The class of the AI service whose tools are guarded. Must not be null.
     * @return The builder.
     */
    static Builder builder(Class<?> aiServiceClass) {
        return ServiceLoader.load(ToolGuardrailServiceBuilderFactory.class)
                .findFirst()
                .map(builderFactory -> builderFactory.getBuilder(aiServiceClass))
                .orElseGet(() -> new ToolGuardrailServiceBuilder(aiServiceClass));
    }

    /**
     * Identifies a tool to the {@link ToolGuardrailService} that bound it (see {@link #bind(Class, AiServiceTool)}),
     * as the key of the {@code execute*} and {@code has*} methods. It is only meaningful to that service: what else it
     * holds is up to the implementation of the service.
     *
     * @since 1.24.0
     */
    @Experimental
    interface ToolKey {
        /**
         * The name of the tool, as the LLM sees it.
         *
         * @return The name of the tool.
         */
        String toolName();
    }

    /**
     * Builds a {@link ToolGuardrailService}. The guardrails and the guardrail classes it is given apply to every tool
     * and run first (see {@link ToolGuardrailService}); the configurations apply to the executors of every tool.
     *
     * @since 1.24.0
     */
    @Experimental
    non-sealed interface Builder
            extends GenericGuardrailService.Builder<
                    ToolInputGuardrailRequest,
                    ToolInputGuardrailResult,
                    ToolOutputGuardrailRequest,
                    ToolOutputGuardrailResult,
                    ToolGuardrailService,
                    ToolInputGuardrail,
                    ToolOutputGuardrail,
                    ToolInputGuardrailsConfig,
                    ToolOutputGuardrailsConfig,
                    Builder> {}
}
