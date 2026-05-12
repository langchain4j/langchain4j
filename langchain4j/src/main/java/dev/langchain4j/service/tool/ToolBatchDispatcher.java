package dev.langchain4j.service.tool;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Reusable primitive for dispatching a batch of {@link ToolExecutionRequest}s against the
 * configured {@link ToolExecutor} map and returning their results in request order.
 *
 * <p>This primitive centralises the logic that was previously scattered between the sync
 * {@code ToolService.execute(...)} loop and the streaming response handler:
 * <ul>
 *   <li>Atomic enforcement of {@code maxToolCallsPerResponse} <em>before</em> any tool runs.</li>
 *   <li>Serial execution when no {@link Executor} is configured, or only one tool is requested
 *       and the caller has not opted into executor dispatch for single-tool batches.</li>
 *   <li>Concurrent execution via {@link CompletableFuture#supplyAsync(java.util.function.Supplier, Executor)}
 *       when an executor is configured and there are multiple tools, or when the caller explicitly
 *       opts into executor dispatch for a single-tool batch.</li>
 *   <li>Ordered gather of results into a {@link LinkedHashMap} keyed by the original request.</li>
 *   <li>Best-effort sibling cancellation when a sibling future fails.</li>
 *   <li>Routing of tool execution exceptions through the configured argument and execution
 *       error handlers, with a caller-supplied bypass {@link Predicate} that lets specific
 *       exception types propagate unchanged.</li>
 *   <li>Invocation of optional {@code beforeToolExecution} and {@code afterToolExecution}
 *       callbacks around each tool dispatch.</li>
 * </ul>
 *
 * <p>This class is intended for internal reuse by {@link ToolService} and the streaming
 * response handler. It is not part of the public API.
 *
 * @since 1.15.0-beta25
 */
@Internal
public final class ToolBatchDispatcher {

    private ToolBatchDispatcher() {}

    /**
     * Configuration for a single batch dispatch.
     */
    @Internal
    public static final class Request {

        private final List<ToolExecutionRequest> toolRequests;
        private final Map<String, ToolExecutor> toolExecutors;
        private final Executor executor;
        private final InvocationContext invocationContext;
        private final Consumer<BeforeToolExecution> beforeToolExecution;
        private final Consumer<ToolExecution> afterToolExecution;
        private final Predicate<Throwable> errorHandlerBypass;
        private final ToolArgumentsErrorHandler argumentsErrorHandler;
        private final ToolExecutionErrorHandler executionErrorHandler;
        private final int maxToolCallsPerResponse;
        private final boolean useExecutorForSingleTool;
        private final Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinationStrategy;

        private Request(Builder b) {
            this.toolRequests = b.toolRequests == null ? List.of() : List.copyOf(b.toolRequests);
            this.toolExecutors = b.toolExecutors == null ? Map.of() : b.toolExecutors;
            this.executor = b.executor;
            this.invocationContext = b.invocationContext;
            this.beforeToolExecution = b.beforeToolExecution;
            this.afterToolExecution = b.afterToolExecution;
            this.errorHandlerBypass = b.errorHandlerBypass == null ? e -> false : b.errorHandlerBypass;
            this.argumentsErrorHandler = b.argumentsErrorHandler;
            this.executionErrorHandler = b.executionErrorHandler;
            this.maxToolCallsPerResponse = b.maxToolCallsPerResponse;
            this.useExecutorForSingleTool = b.useExecutorForSingleTool;
            this.hallucinationStrategy = b.hallucinationStrategy;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private List<ToolExecutionRequest> toolRequests;
            private Map<String, ToolExecutor> toolExecutors;
            private Executor executor;
            private InvocationContext invocationContext;
            private Consumer<BeforeToolExecution> beforeToolExecution;
            private Consumer<ToolExecution> afterToolExecution;
            private Predicate<Throwable> errorHandlerBypass;
            private ToolArgumentsErrorHandler argumentsErrorHandler;
            private ToolExecutionErrorHandler executionErrorHandler;
            private int maxToolCallsPerResponse = 0;
            private boolean useExecutorForSingleTool = false;
            private Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinationStrategy;

            private Builder() {}

            public Builder toolRequests(List<ToolExecutionRequest> toolRequests) {
                this.toolRequests = toolRequests;
                return this;
            }

            public Builder toolExecutors(Map<String, ToolExecutor> toolExecutors) {
                this.toolExecutors = toolExecutors;
                return this;
            }

            public Builder executor(Executor executor) {
                this.executor = executor;
                return this;
            }

            public Builder invocationContext(InvocationContext invocationContext) {
                this.invocationContext = invocationContext;
                return this;
            }

            public Builder beforeToolExecution(Consumer<BeforeToolExecution> beforeToolExecution) {
                this.beforeToolExecution = beforeToolExecution;
                return this;
            }

            public Builder afterToolExecution(Consumer<ToolExecution> afterToolExecution) {
                this.afterToolExecution = afterToolExecution;
                return this;
            }

            public Builder errorHandlerBypass(Predicate<Throwable> errorHandlerBypass) {
                this.errorHandlerBypass = errorHandlerBypass;
                return this;
            }

            public Builder argumentsErrorHandler(ToolArgumentsErrorHandler argumentsErrorHandler) {
                this.argumentsErrorHandler = argumentsErrorHandler;
                return this;
            }

            public Builder executionErrorHandler(ToolExecutionErrorHandler executionErrorHandler) {
                this.executionErrorHandler = executionErrorHandler;
                return this;
            }

            public Builder maxToolCallsPerResponse(int maxToolCallsPerResponse) {
                this.maxToolCallsPerResponse = maxToolCallsPerResponse;
                return this;
            }

            public Builder useExecutorForSingleTool(boolean useExecutorForSingleTool) {
                this.useExecutorForSingleTool = useExecutorForSingleTool;
                return this;
            }

            public Builder hallucinationStrategy(Function<ToolExecutionRequest, ToolExecutionResultMessage> strategy) {
                this.hallucinationStrategy = strategy;
                return this;
            }

            public Request build() {
                return new Request(this);
            }
        }
    }

    /**
     * Dispatches the configured batch of tool calls.
     *
     * <p>If {@link Request#maxToolCallsPerResponse} is {@code > 0} and the batch contains more than
     * that many requests, a {@link ToolCallsLimitExceededException} is thrown <em>before</em> any
     * tool is executed.
     *
     * <p>If {@link Request#executor} is non-{@code null} and the batch contains more than one
     * request, tools are dispatched concurrently using the supplied executor. Single-tool batches
     * use the executor only when {@link Request#useExecutorForSingleTool} is {@code true}.
     * Otherwise tools are executed sequentially on the calling thread.
     *
     * <p>The returned map preserves the order of the original {@code toolRequests} list.
     *
     * @return a map of {@link ToolExecutionRequest} to its {@link ToolExecutionResult}, keyed by
     *         the original request and ordered consistently with the input list.
     * @throws ToolCallsLimitExceededException if the cap is configured and exceeded
     */
    public static Map<ToolExecutionRequest, ToolExecutionResult> dispatch(Request request) {
        // Atomic cap check. No tool from this batch must run if the cap is exceeded.
        if (request.maxToolCallsPerResponse > 0
                && request.toolRequests.size() > request.maxToolCallsPerResponse) {
            throw new ToolCallsLimitExceededException(
                    request.maxToolCallsPerResponse, request.toolRequests.size());
        }

        if (request.toolRequests.isEmpty()) {
            return Collections.emptyMap();
        }

        // Serial path: no executor, or a single tool whose caller did not opt into executor dispatch.
        if (request.executor == null
                || (request.toolRequests.size() == 1 && !request.useExecutorForSingleTool)) {
            return executeSerially(request);
        }

        return executeConcurrently(request);
    }

    private static Map<ToolExecutionRequest, ToolExecutionResult> executeSerially(Request request) {
        Map<ToolExecutionRequest, ToolExecutionResult> results = new LinkedHashMap<>();
        for (ToolExecutionRequest toolRequest : request.toolRequests) {
            results.put(toolRequest, executeOne(request, toolRequest));
        }
        return results;
    }

    private static Map<ToolExecutionRequest, ToolExecutionResult> executeConcurrently(Request request) {
        Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> futures = new LinkedHashMap<>();
        List<CompletableFuture<ToolExecutionResult>> futureList = new ArrayList<>();

        for (ToolExecutionRequest toolRequest : request.toolRequests) {
            CompletableFuture<ToolExecutionResult> future = CompletableFuture.supplyAsync(
                    () -> executeOne(request, toolRequest), request.executor);
            futures.put(toolRequest, future);
            futureList.add(future);
        }

        Map<ToolExecutionRequest, ToolExecutionResult> results = new LinkedHashMap<>();
        for (Map.Entry<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().get());
            } catch (ExecutionException e) {
                cancelSiblings(futureList, entry.getValue());
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                    throw re;
                } else if (cause instanceof Error err) {
                    throw err;
                } else {
                    throw new RuntimeException(cause);
                }
            } catch (InterruptedException e) {
                cancelSiblings(futureList, entry.getValue());
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        return results;
    }

    private static void cancelSiblings(
            List<CompletableFuture<ToolExecutionResult>> all,
            CompletableFuture<ToolExecutionResult> failed) {
        for (CompletableFuture<ToolExecutionResult> future : all) {
            if (future != failed && !future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private static ToolExecutionResult executeOne(Request request, ToolExecutionRequest toolRequest) {
        if (request.beforeToolExecution != null) {
            request.beforeToolExecution.accept(BeforeToolExecution.builder()
                    .request(toolRequest)
                    .invocationContext(request.invocationContext)
                    .build());
        }

        LocalDateTime startTime = LocalDateTime.now();

        ToolExecutor toolExecutor = request.toolExecutors.get(toolRequest.name());
        ToolExecutionResult toolResult;
        if (toolExecutor == null) {
            toolResult = applyHallucinationStrategy(request, toolRequest);
        } else {
            toolResult = ToolService.executeWithErrorHandling(
                    toolRequest,
                    toolExecutor,
                    request.invocationContext,
                    request.argumentsErrorHandler,
                    request.executionErrorHandler,
                    request.errorHandlerBypass);
        }

        if (request.afterToolExecution != null) {
            request.afterToolExecution.accept(ToolExecution.builder()
                    .request(toolRequest)
                    .result(toolResult)
                    .startTime(startTime)
                    .finishTime(LocalDateTime.now())
                    .invocationContext(request.invocationContext)
                    .build());
        }
        return toolResult;
    }

    private static ToolExecutionResult applyHallucinationStrategy(Request request, ToolExecutionRequest toolRequest) {
        Function<ToolExecutionRequest, ToolExecutionResultMessage> strategy =
                request.hallucinationStrategy != null
                        ? request.hallucinationStrategy
                        : HallucinatedToolNameStrategy.THROW_EXCEPTION;
        ToolExecutionResultMessage message = strategy.apply(toolRequest);
        return ToolExecutionResult.builder().resultText(message.text()).build();
    }

    /**
     * Convenience helper used by callers that don't need to participate in the full
     * {@link Request} configuration. Computes whether the loop should return immediately based on
     * the resulting batch's {@link ReturnBehavior}s.
     */
    public static boolean shouldReturnImmediately(boolean anyToolErrored, List<ReturnBehavior> returnBehaviors) {
        return ToolService.shouldReturnImmediately(anyToolErrored, returnBehaviors);
    }
}
