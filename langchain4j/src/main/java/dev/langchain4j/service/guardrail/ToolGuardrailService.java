package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.CompletableFutureUtils.failOnThrow;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.ToolGuardrailStage;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailException;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailException;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import dev.langchain4j.internal.Exceptions;
import dev.langchain4j.service.guardrail.spi.ToolGuardrailServiceBuilderFactory;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.spi.classloading.ClassInstanceFactory;
import dev.langchain4j.spi.classloading.ClassMetadataProviderFactory;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;

/**
 * Executes the tool guardrails of the tools of an AI service: the {@link ToolInputGuardrail}s, which validate or
 * rewrite the arguments of a tool call before the tool runs, and the {@link ToolOutputGuardrail}s, which validate or
 * rewrite its result before the LLM sees it. It is the tool counterpart of {@link GuardrailService}, and its
 * {@link #targetClass()} is the AI service class.
 * <p>
 *     <strong>Keys.</strong> The key of a tool is the {@link ToolKey} that {@link #bind(Class, AiServiceTool)}
 *     returns for it: it is only meaningful to the service that created it. A tool provided anew for every
 *     invocation (by a {@link ToolProvider}, for example an MCP server) is bound for every
 *     invocation. The key carries the specification and the source of the tool, so the guardrail requests are built
 *     from it.
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
 *     {@link ToolInputGuardrailException} or a
 *     {@link ToolOutputGuardrailException} (the asynchronous methods fail their future
 *     with it). {@link #executeGuardrails(ToolKey, ToolExecutionRequest, GuardrailRequestParams, Function)} runs a
 *     whole tool call through the guardrails and applies these rules, the way
 *     {@link GuardrailService#executeGuardrails(Object, InputGuardrailRequest)} does for the AI service guardrails;
 *     {@link #executeGuardrailsAsync(ToolKey, ToolExecutionRequest, GuardrailRequestParams, Function)} is its
 *     non-blocking counterpart, for which the guardrails must implement
 *     {@link Guardrail#validateAsync(GuardrailRequest)}.
 * </p>
 * <p>
 *     <strong>Customizing.</strong> The default implementation finds the annotations of the classes of the tools
 *     through {@link ClassMetadataProviderFactory}, reads the annotations of the tool methods from the {@code Method}
 *     itself, and creates guardrails from their classes through {@link ClassInstanceFactory}, like the AI service
 *     guardrails. A framework can also replace the whole builder with a {@link ToolGuardrailServiceBuilderFactory}.
 *     An implementation must be thread-safe: one AI service runs many invocations at the same time, and the tools of
 *     one LLM response can run in parallel.
 * </p>
 * <p>
 *     This interface is separate from {@link GuardrailService}: the two kinds of guardrails share their
 *     implementation, not a public contract.
 * </p>
 *
 * @since 1.25.0
 */
@Experimental
public interface ToolGuardrailService {

    /**
     * The class the tool guardrails of this service apply to: the AI service class whose tools are guarded.
     *
     * @return The AI service class.
     */
    Class<?> targetClass();

    /**
     * Binds a tool to this service: resolves the tool guardrails that apply to the tool, and returns the key to run
     * them with.
     * <p>
     *     A framework that runs its own tool loop, and has no {@code Method} for a tool, passes the guardrails of the
     *     class and of the method of the tool as the guardrails of the {@link AiServiceTool}, and calls
     *     {@code bind(null, tool)} with a tool whose executor is not a {@code DefaultToolExecutor}: no annotation is
     *     read then, and the chain of the tool is the guardrails given to the builder, followed by the
     *     {@link AiServiceTool} guardrails.
     * </p>
     *
     * @param toolClass The class whose {@link ToolInputGuardrails} / {@link ToolOutputGuardrails} annotations apply to
     *                  the tool: the class of the object the {@code @Tool} method of the tool was found on (the object
     *                  given to {@code AiServices.tools(..)}). {@code null} for a tool that was not found on an object
     *                  (an {@link AiServiceTool} given directly, or provided by a
     *                  {@link ToolProvider} such as an MCP server); the declaring class of
     *                  the tool method is then used, if the tool has one.
     * @param tool      The tool. Must not be null.
     * @return The {@link ToolKey} that identifies this tool to this service: pass it to
     *         {@link #executeInputGuardrails(ToolKey, ToolInputGuardrailRequest)},
     *         {@link #executeOutputGuardrails(ToolKey, ToolOutputGuardrailRequest)}, their asynchronous variants,
     *         {@link #hasInputGuardrails(ToolKey)} / {@link #hasOutputGuardrails(ToolKey)} and
     *         {@link #executeGuardrails(ToolKey, ToolExecutionRequest, GuardrailRequestParams, Function)}. It carries
     *         the specification and the source of the tool, is only meaningful to the service that returned it, and is
     *         never {@code null}. A tool without guardrails also gets a key, and running its guardrails returns a
     *         success. The default implementation rejects, with an {@link IllegalArgumentException}, a {@code null}
     *         key and any key it did not return itself, including a key of another service built the same way.
     */
    ToolKey bind(@Nullable Class<?> toolClass, AiServiceTool tool);

    /**
     * Executes the input guardrails of the tool.
     *
     * @param key     The key {@link #bind(Class, AiServiceTool)} returned for the tool. Must not be null.
     * @param request The request to validate. Must not be null.
     * @return The result, a success if the tool has no input guardrails. A non-fatal failure is returned, not thrown.
     * @throws ToolInputGuardrailException for a fatal failure
     */
    ToolInputGuardrailResult executeInputGuardrails(ToolKey key, ToolInputGuardrailRequest request);

    /**
     * Non-blocking counterpart of {@link #executeInputGuardrails(ToolKey, ToolInputGuardrailRequest)}: nothing is
     * thrown, a fatal failure or an invalid key fails the returned future.
     *
     * @param key     The key {@link #bind(Class, AiServiceTool)} returned for the tool. Must not be null.
     * @param request The request to validate. Must not be null.
     * @return A future of the result.
     */
    CompletableFuture<ToolInputGuardrailResult> executeInputGuardrailsAsync(
            ToolKey key, ToolInputGuardrailRequest request);

    /**
     * Executes the output guardrails of the tool.
     *
     * @param key     The key {@link #bind(Class, AiServiceTool)} returned for the tool. Must not be null.
     * @param request The request to validate. Must not be null.
     * @return The result, a success if the tool has no output guardrails. A non-fatal failure is returned, not thrown.
     * @throws ToolOutputGuardrailException for a fatal failure
     */
    ToolOutputGuardrailResult executeOutputGuardrails(ToolKey key, ToolOutputGuardrailRequest request);

    /**
     * Non-blocking counterpart of {@link #executeOutputGuardrails(ToolKey, ToolOutputGuardrailRequest)}: nothing is
     * thrown, a fatal failure or an invalid key fails the returned future.
     *
     * @param key     The key {@link #bind(Class, AiServiceTool)} returned for the tool. Must not be null.
     * @param request The request to validate. Must not be null.
     * @return A future of the result.
     */
    CompletableFuture<ToolOutputGuardrailResult> executeOutputGuardrailsAsync(
            ToolKey key, ToolOutputGuardrailRequest request);

    /**
     * Whether the tool has input guardrails.
     *
     * @param key The key {@link #bind(Class, AiServiceTool)} returned for the tool. Must not be null.
     * @return {@code true} if at least one input guardrail applies to the tool.
     */
    boolean hasInputGuardrails(ToolKey key);

    /**
     * Whether the tool has output guardrails.
     *
     * @param key The key {@link #bind(Class, AiServiceTool)} returned for the tool. Must not be null.
     * @return {@code true} if at least one output guardrail applies to the tool.
     */
    boolean hasOutputGuardrails(ToolKey key);

    /**
     * Executes a tool call through the tool guardrails of the tool: the input guardrails, then {@code tool}, then the
     * output guardrails. It is the tool counterpart of {@link GuardrailService#executeGuardrails(Object,
     * InputGuardrailRequest)}: it builds the guardrail requests and turns the guardrail
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
     *     {@link ToolOutputGuardrailException}. It is thrown outside {@code tool}, so the
     *     error handling of the tool does not see it. An exception thrown by {@code tool} propagates as is. When a
     *     fatal input failure ends the call, {@link #rejectedRequest(ToolExecutionRequest, Throwable)} gives the
     *     request as the guardrails before it left it.</li>
     *     <li>A {@link ToolArgumentsException} thrown by an input guardrail, for example by
     *     {@link ToolInputGuardrailRequest#argumentsAsMap()} for arguments that cannot be parsed, is not a guardrail
     *     failure: {@code tool} does not run, and the exception is thrown as is, so that the caller handles it like an
     *     arguments error of the tool.</li>
     * </ul>
     *
     * @param key           The key {@link #bind(Class, AiServiceTool)} returned for the tool. It also gives the
     *                      specification and the source of the tool to the guardrail requests. Must not be null.
     * @param request       The tool call of the LLM. Must not be null.
     * @param requestParams The parameters of the guardrail requests: those of the AI service invocation, so the tool
     *                      guardrails see the same context as the AI service guardrails and their events reach the
     *                      listeners of the invocation. Must not be null.
     * @param tool          Executes the tool for the request it is given (the call, or the call as the input
     *                      guardrails rewrote it) and returns its result. Must not be null.
     * @return The request as the input guardrails left it, and the result to send to the LLM.
     * @throws IllegalArgumentException if a parameter is {@code null}, the key is one this service rejects (see
     *                                  {@link #bind(Class, AiServiceTool)}), or the key is not the key of the tool
     *                                  that {@code request} calls
     */
    default GuardedToolExecution executeGuardrails(
            ToolKey key,
            ToolExecutionRequest request,
            GuardrailRequestParams requestParams,
            Function<ToolExecutionRequest, ToolExecutionResult> tool) {

        ensureNotNull(key, "key");
        ensureNotNull(request, "request");
        ensureNotNull(requestParams, "requestParams");
        ensureNotNull(tool, "tool");
        ensureKeyOfTheCalledTool(key, request);

        return hasGuardrails(key)
                ? guard(key, request, requestParams, tool)
                : guardedExecution(request, tool.apply(request));
    }

    private static void ensureKeyOfTheCalledTool(ToolKey key, ToolExecutionRequest request) {
        if (!key.toolName().equals(request.name())) {
            throw new IllegalArgumentException("The key of the tool '%s' was given for a call of the tool '%s'"
                    .formatted(key.toolName(), request.name()));
        }
    }

    private GuardedToolExecution guard(
            ToolKey key,
            ToolExecutionRequest request,
            GuardrailRequestParams requestParams,
            Function<ToolExecutionRequest, ToolExecutionResult> tool) {

        var inputResult = hasInputGuardrails(key)
                ? executeInputGuardrails(key, inputRequest(key, request, requestParams))
                : ToolInputGuardrailResult.success();

        var effectiveRequest = effectiveRequest(inputResult, request);

        return inputResult.isSuccess()
                ? executeToolAndOutputGuardrails(key, effectiveRequest, requestParams, tool)
                : guardedExecution(
                        effectiveRequest,
                        errorResult(inputResult.failureMessage()),
                        Optional.of(ToolGuardrailStage.INPUT));
    }

    private GuardedToolExecution executeToolAndOutputGuardrails(
            ToolKey key,
            ToolExecutionRequest request,
            GuardrailRequestParams requestParams,
            Function<ToolExecutionRequest, ToolExecutionResult> tool) {

        var toolResult = tool.apply(request);
        var guardedResult = applyOutputGuardrails(key, request, toolResult, requestParams);

        return guardedExecution(request, guardedResult, failedOutputStage(toolResult, guardedResult));
    }

    /**
     * Non-blocking counterpart of {@link #executeGuardrails(ToolKey, ToolExecutionRequest, GuardrailRequestParams,
     * Function)}, with the same rules, for the asynchronous ({@code CompletableFuture}) and reactive
     * ({@code Flow.Publisher}) AI service modes. The guardrails run with
     * {@link #executeInputGuardrailsAsync(ToolKey, ToolInputGuardrailRequest)} and
     * {@link #executeOutputGuardrailsAsync(ToolKey, ToolOutputGuardrailRequest)}, so a guardrail that
     * overrides {@link Guardrail#validateAsync(GuardrailRequest)}
     * does not block the calling thread.
     * <p>
     *     Nothing is thrown from this method: a fatal guardrail failure, an exception thrown by a guardrail or by
     *     {@code tool} (including the {@link ToolArgumentsException} of an input
     *     guardrail), a failed future of {@code tool}, a {@code null} parameter, a key this service rejects and a key
     *     of another tool than the one {@code request} calls all fail the returned future. Canceling the returned future cancels the guardrails or the tool in flight.
     * </p>
     *
     * @param key           The key {@link #bind(Class, AiServiceTool)} returned for the tool. It also gives the
     *                      specification and the source of the tool to the guardrail requests. Must not be null.
     * @param request       The tool call of the LLM. Must not be null.
     * @param requestParams The parameters of the guardrail requests: those of the AI service invocation. Must not be
     *                      null.
     * @param tool          Starts the tool for the request it is given (the call, or the call as the input
     *                      guardrails rewrote it) and returns the future of its result. Must not be null.
     * @return A future of the request as the input guardrails left it and of the result to send to the LLM.
     */
    default CompletableFuture<GuardedToolExecution> executeGuardrailsAsync(
            ToolKey key,
            ToolExecutionRequest request,
            GuardrailRequestParams requestParams,
            Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> tool) {

        return failOnThrow(() -> {
            ensureNotNull(key, "key");
            ensureNotNull(request, "request");
            ensureNotNull(requestParams, "requestParams");
            ensureNotNull(tool, "tool");
            ensureKeyOfTheCalledTool(key, request);

            return hasGuardrails(key)
                    ? guardAsync(key, request, requestParams, tool)
                    : executeWithoutGuardrailsAsync(request, tool);
        });
    }

    /**
     * The request of a call that a tool guardrail failure ended, as the guardrails left it, when the failure tells:
     * after a fatal input failure, the call with the arguments the failing guardrail validated (as the guardrails
     * before it rewrote them). It lets a caller report a call that
     * {@link #executeGuardrails(ToolKey, ToolExecutionRequest, GuardrailRequestParams, Function)} or
     * {@link #executeGuardrailsAsync(ToolKey, ToolExecutionRequest, GuardrailRequestParams, Function)} ended with an
     * exception without knowing the exceptions of the guardrails.
     * <p>
     *     Empty for any other failure: a failure of the tool or of an output guardrail ends a call that the tool was
     *     started with, and the caller knows that request from its {@code tool} function.
     * </p>
     *
     * @param request The tool call of the LLM. Must not be null.
     * @param failure The exception that ended the call, possibly wrapped in a
     *                {@link CompletionException}; may be null.
     * @return The request as the guardrails left it, if the failure tells.
     */
    default Optional<ToolExecutionRequest> rejectedRequest(ToolExecutionRequest request, @Nullable Throwable failure) {
        ensureNotNull(request, "request");
        return Optional.ofNullable(failure)
                .map(Exceptions::unwrapCompletionException)
                .filter(ToolInputGuardrailException.class::isInstance)
                .map(ToolInputGuardrailException.class::cast)
                .flatMap(ToolInputGuardrailException::arguments)
                .map(arguments -> request.toBuilder().arguments(arguments).build());
    }

    /**
     * The stage whose guardrails ended a call with a fatal failure, as {@link #rejectedRequest(ToolExecutionRequest,
     * Throwable)} gives its request. It lets a caller tell what ended a call that
     * {@link #executeGuardrails(ToolKey, ToolExecutionRequest, GuardrailRequestParams, Function)} or
     * {@link #executeGuardrailsAsync(ToolKey, ToolExecutionRequest, GuardrailRequestParams, Function)} ended with an
     * exception without knowing the exceptions of the guardrails, and
     * without inferring it from what it saw of the call: a failure of the call before the guardrails ran, such as a
     * callback that threw, is not one of a stage.
     * <p>
     *     Empty for any other failure, including a cancellation. A non-fatal failure returns an execution, which tells
     *     its stage with {@link GuardedToolExecution#failedStage()}.
     * </p>
     *
     * @param request The tool call of the LLM. Must not be null.
     * @param failure The exception that ended the call, possibly wrapped in a
     *                {@link CompletionException}; may be null.
     * @return The stage of the fatal failure, if the failure is one.
     */
    default Optional<ToolGuardrailStage> rejectedStage(ToolExecutionRequest request, @Nullable Throwable failure) {
        ensureNotNull(request, "request");
        return Optional.ofNullable(failure)
                .map(Exceptions::unwrapCompletionException)
                .flatMap(ToolGuardrailService::stageOf);
    }

    /**
     * The stage whose guardrails threw {@code error}, if a tool guardrail threw it.
     */
    private static Optional<ToolGuardrailStage> stageOf(Throwable error) {
        ToolGuardrailStage stage = null;

        if (error instanceof ToolInputGuardrailException) {
            stage = ToolGuardrailStage.INPUT;
        } else if (error instanceof ToolOutputGuardrailException) {
            stage = ToolGuardrailStage.OUTPUT;
        }

        return Optional.ofNullable(stage);
    }

    private boolean hasGuardrails(ToolKey key) {
        return hasInputGuardrails(key) || hasOutputGuardrails(key);
    }

    private static CompletableFuture<GuardedToolExecution> executeWithoutGuardrailsAsync(
            ToolExecutionRequest request, Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> tool) {
        var toolResult = failOnThrow(tool, "apply(ToolExecutionRequest)", () -> tool.apply(request));
        var execution = toolResult.thenApply(result -> guardedExecution(request, result));
        propagateCancellation(execution, toolResult);
        return execution;
    }

    private CompletableFuture<GuardedToolExecution> guardAsync(
            ToolKey key,
            ToolExecutionRequest request,
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
                        result,
                        inFlight,
                        () -> executeInputGuardrailsAsync(key, inputRequest(key, request, requestParams)),
                        this,
                        "executeInputGuardrailsAsync(ToolKey, ToolInputGuardrailRequest)")
                : CompletableFuture.completedFuture(ToolInputGuardrailResult.success());

        inputStage
                .thenCompose(inputResult -> {
                    var effectiveRequest = effectiveRequest(inputResult, request);
                    return inputResult.isSuccess()
                            ? startStage(
                                            result,
                                            inFlight,
                                            () -> tool.apply(effectiveRequest),
                                            tool,
                                            "apply(ToolExecutionRequest)")
                                    .thenCompose(toolResult -> applyOutputGuardrailsAsync(
                                                    result, inFlight, key, effectiveRequest, toolResult, requestParams)
                                            .thenApply(guardedResult -> guardedExecution(
                                                    effectiveRequest,
                                                    guardedResult,
                                                    failedOutputStage(toolResult, guardedResult))))
                            : CompletableFuture.completedFuture(guardedExecution(
                                    effectiveRequest,
                                    errorResult(inputResult.failureMessage()),
                                    Optional.of(ToolGuardrailStage.INPUT)));
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
     * Starts a stage of {@link #executeGuardrailsAsync}, turning anything thrown while starting it into a failed
     * future (and a {@code null} future into one that names {@code method} of {@code target}), and makes it the stage
     * that a cancellation cancels. Once {@code execution} is canceled, no stage starts
     * any more: a cancellation that comes after the previous stage ended, but before this one is the stage in flight,
     * would otherwise cancel nothing.
     */
    private static <T> CompletableFuture<T> startStage(
            CompletableFuture<?> execution,
            AtomicReference<CompletableFuture<?>> inFlight,
            Supplier<CompletableFuture<T>> stage,
            Object target,
            String method) {
        CompletableFuture<T> future = execution.isCancelled()
                ? CompletableFuture.failedFuture(new CancellationException())
                : failOnThrow(target, method, stage);
        inFlight.set(future);

        // a cancellation that came after the check above canceled the previous stage, not this one
        if (execution.isCancelled()) {
            future.cancel(true);
        }

        return future;
    }

    private CompletableFuture<ToolExecutionResult> applyOutputGuardrailsAsync(
            CompletableFuture<?> execution,
            AtomicReference<CompletableFuture<?>> inFlight,
            ToolKey key,
            ToolExecutionRequest request,
            ToolExecutionResult toolResult,
            GuardrailRequestParams requestParams) {

        CompletableFuture<ToolExecutionResult> guarded;

        if (hasOutputGuardrails(key)) {
            var guardrailRequest = outputRequest(key, request, toolResult, requestParams);
            guarded = startStage(
                            execution,
                            inFlight,
                            () -> executeOutputGuardrailsAsync(key, guardrailRequest),
                            this,
                            "executeOutputGuardrailsAsync(ToolKey, ToolOutputGuardrailRequest)")
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
            GuardrailRequestParams requestParams) {
        return hasOutputGuardrails(key) ? guardToolResult(key, request, toolResult, requestParams) : toolResult;
    }

    private ToolExecutionResult guardToolResult(
            ToolKey key,
            ToolExecutionRequest request,
            ToolExecutionResult toolResult,
            GuardrailRequestParams requestParams) {

        var guardrailRequest = outputRequest(key, request, toolResult, requestParams);

        return guardedResult(executeOutputGuardrails(key, guardrailRequest), guardrailRequest, toolResult);
    }

    private static ToolInputGuardrailRequest inputRequest(
            ToolKey key, ToolExecutionRequest request, GuardrailRequestParams requestParams) {

        return ToolInputGuardrailRequest.builder()
                .toolExecutionRequest(request)
                .toolSpecification(key.toolSpecification())
                .toolSource(key.toolSource())
                .requestParams(requestParams)
                .build();
    }

    private static ToolOutputGuardrailRequest outputRequest(
            ToolKey key,
            ToolExecutionRequest request,
            ToolExecutionResult toolResult,
            GuardrailRequestParams requestParams) {

        return ToolOutputGuardrailRequest.builder()
                .toolExecutionRequest(request)
                .toolSpecification(key.toolSpecification())
                .toolSource(key.toolSource())
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

        if (!outputResult.isSuccess()) {
            return errorResult(outputResult.failureMessage());
        } else if (outputResult.hasRewrittenResult()) {
            return ToolExecutionResult.builder()
                    .resultContents(outputResult.resultContents(guardrailRequest))
                    .isError(outputResult.isError(guardrailRequest))
                    .attributes(toolResult.attributes())
                    .build();
        }

        return toolResult;
    }

    private static GuardedToolExecution guardedExecution(ToolExecutionRequest request, ToolExecutionResult result) {
        return guardedExecution(request, result, Optional.empty());
    }

    private static GuardedToolExecution guardedExecution(
            ToolExecutionRequest request, ToolExecutionResult result, Optional<ToolGuardrailStage> failedStage) {
        return GuardedToolExecution.builder()
                .request(request)
                .result(result)
                .failedStage(failedStage.orElse(null))
                .build();
    }

    /**
     * The output stage failed the call when its guardrails turned the successful result of the tool into an error
     * result. A tool that failed on its own did not fail because of a guardrail, whatever the guardrails then did
     * with its error result.
     */
    private static Optional<ToolGuardrailStage> failedOutputStage(
            ToolExecutionResult toolResult, ToolExecutionResult guardedResult) {
        return (guardedResult.isError() && !toolResult.isError())
                ? Optional.of(ToolGuardrailStage.OUTPUT)
                : Optional.empty();
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
     * as the key of the {@code execute*} and {@code has*} methods. It carries the specification and the source of the
     * tool, which the guardrail requests are built from. It is only meaningful to the service that bound it: what else
     * it holds is up to the implementation of the service.
     *
     * @since 1.25.0
     */
    @Experimental
    interface ToolKey {
        /**
         * The specification of the tool, as the LLM sees it.
         *
         * @return The specification, never {@code null}.
         */
        ToolSpecification toolSpecification();

        /**
         * Where the tool comes from.
         *
         * @return The source, never {@code null}.
         */
        ToolSource toolSource();

        /**
         * The name of the tool, as the LLM sees it.
         *
         * @return The name of the tool.
         */
        default String toolName() {
            return toolSpecification().name();
        }
    }

    /**
     * Builds a {@link ToolGuardrailService}. The guardrails and the guardrail classes it is given apply to every tool
     * and run first (see {@link ToolGuardrailService}); the configurations apply to the executors of every tool.
     * Setting guardrails, or guardrail classes, replaces what was set before, and {@code null} clears them.
     *
     * @since 1.25.0
     */
    @Experimental
    interface Builder {

        /**
         * Configures the input guardrails of every tool.
         *
         * @param config The configuration for the input guardrails. Must not be null.
         * @return This builder.
         * @throws IllegalArgumentException if {@code config} is null.
         */
        Builder inputGuardrailsConfig(ToolInputGuardrailsConfig config);

        /**
         * Configures the output guardrails of every tool.
         *
         * @param config The configuration for the output guardrails. Must not be null.
         * @return This builder.
         * @throws IllegalArgumentException if {@code config} is null.
         */
        Builder outputGuardrailsConfig(ToolOutputGuardrailsConfig config);

        /**
         * Sets the classes of the input guardrails that apply to every tool, replacing the ones set before.
         *
         * @param guardrailClasses The classes of the input guardrails. May be {@code null}.
         * @param <I> The type of the input guardrails
         * @return This builder.
         */
        <I extends ToolInputGuardrail> Builder inputGuardrailClasses(@Nullable List<Class<? extends I>> guardrailClasses);

        /**
         * Sets the classes of the input guardrails that apply to every tool, replacing the ones set before.
         *
         * @param guardrailClasses The classes of the input guardrails. May be {@code null}.
         * @param <I> The type of the input guardrails
         * @return This builder.
         */
        @SuppressWarnings("unchecked")
        default <I extends ToolInputGuardrail> Builder inputGuardrailClasses(Class<? extends I>... guardrailClasses) {
            return inputGuardrailClasses((guardrailClasses != null) ? List.of(guardrailClasses) : null);
        }

        /**
         * Sets the classes of the output guardrails that apply to every tool, replacing the ones set before.
         *
         * @param guardrailClasses The classes of the output guardrails. May be {@code null}.
         * @param <O> The type of the output guardrails
         * @return This builder.
         */
        <O extends ToolOutputGuardrail> Builder outputGuardrailClasses(@Nullable List<Class<? extends O>> guardrailClasses);

        /**
         * Sets the classes of the output guardrails that apply to every tool, replacing the ones set before.
         *
         * @param guardrailClasses The classes of the output guardrails. May be {@code null}.
         * @param <O> The type of the output guardrails
         * @return This builder.
         */
        @SuppressWarnings("unchecked")
        default <O extends ToolOutputGuardrail> Builder outputGuardrailClasses(
                Class<? extends O>... guardrailClasses) {
            return outputGuardrailClasses((guardrailClasses != null) ? List.of(guardrailClasses) : null);
        }

        /**
         * Sets the input guardrails that apply to every tool, replacing the ones set before.
         *
         * @param guardrails The input guardrails. May be {@code null}.
         * @param <I> The type of the input guardrails
         * @return This builder.
         */
        <I extends ToolInputGuardrail> Builder inputGuardrails(@Nullable List<I> guardrails);

        /**
         * Sets the input guardrails that apply to every tool, replacing the ones set before.
         *
         * @param guardrails The input guardrails. May be {@code null}.
         * @param <I> The type of the input guardrails
         * @return This builder.
         */
        @SuppressWarnings("unchecked")
        default <I extends ToolInputGuardrail> Builder inputGuardrails(I... guardrails) {
            return inputGuardrails((guardrails != null) ? List.of(guardrails) : null);
        }

        /**
         * Sets the output guardrails that apply to every tool, replacing the ones set before.
         *
         * @param guardrails The output guardrails. May be {@code null}.
         * @param <O> The type of the output guardrails
         * @return This builder.
         */
        <O extends ToolOutputGuardrail> Builder outputGuardrails(@Nullable List<O> guardrails);

        /**
         * Sets the output guardrails that apply to every tool, replacing the ones set before.
         *
         * @param guardrails The output guardrails. May be {@code null}.
         * @param <O> The type of the output guardrails
         * @return This builder.
         */
        @SuppressWarnings("unchecked")
        default <O extends ToolOutputGuardrail> Builder outputGuardrails(O... guardrails) {
            return outputGuardrails((guardrails != null) ? List.of(guardrails) : null);
        }

        /**
         * Builds the service.
         *
         * @return The service, configured with the guardrails and the configurations the builder was given.
         */
        ToolGuardrailService build();
    }
}
