package dev.langchain4j.service.tool;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.internal.AsyncNotSupported;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.MemoryId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A low-level executor/handler of a {@link ToolExecutionRequest}.
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * Executes a tool request.
     *
     * @param request  The tool execution request. Contains tool name and arguments.
     * @param memoryId The ID of the chat memory. .
     * @return The result of the tool execution that will be sent to the LLM.
     */
    String execute(ToolExecutionRequest request, Object memoryId);

    /**
     * Executes a tool request. Override this method if you wish to:
     * <pre>
     * - access the {@link InvocationParameters} when passing extra data into the tool
     * - propagate the tool result object ({@link ToolExecutionResult#result()}) into the {@link ToolExecution}
     * </pre>
     *
     * @param request The tool execution request. Contains tool name and arguments.
     * @param context The AI Service invocation context, contains {@link ChatMemory} ID
     *                (see {@link MemoryId} for more details), and {@link InvocationParameters}.
     * @return The result of the tool execution that will be sent to the LLM.
     */
    default ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        Object memoryId = context == null ? null : context.chatMemoryId();

        String result = execute(request, memoryId);

        return ToolExecutionResult.builder()
                .resultText(result)
                .build();
    }

    /**
     * Non-blocking counterpart of {@link #executeWithContext(ToolExecutionRequest, InvocationContext)},
     * invoked by the asynchronous AI Service tool loop (AI Service methods returning {@link CompletableFuture}
     * or {@link CompletionStage}), which composes the returned future instead of waiting on a thread.
     * <p>
     * The default implementation returns a failed future carrying {@link AsyncNotSupportedException}: asynchronous
     * AI Services are opt-in, and silently executing a tool synchronously there would block the thread delivering
     * model responses without any visible signal. This failure is not passed to the tool error handlers
     * (and thus never reaches the LLM) — it fails the AI Service invocation, making the gap visible.
     * <p>
     * Override this method to use this tool with an asynchronous AI Service. If the tool performs I/O that
     * can be initiated without holding a thread (e.g. it delegates to an asynchronous client), return that
     * future. If blocking execution is acceptable, it can simply return
     * {@code CompletableFuture.completedFuture(executeWithContext(request, context))}.
     * <p>
     * Errors may be signaled either synchronously (thrown from this method) or via a failed future;
     * the AI Service applies the configured tool error handlers to both identically.
     *
     * @param request The tool execution request. Contains tool name and arguments.
     * @param context The AI Service invocation context, contains {@link ChatMemory} ID
     *                (see {@link MemoryId} for more details), and {@link InvocationParameters}.
     * @return a {@link CompletableFuture} of the result of the tool execution that will be sent to the LLM
     * @since 1.19.0
     */
    default CompletableFuture<ToolExecutionResult> executeAsync(
            ToolExecutionRequest request, InvocationContext context) {
        return AsyncNotSupported.failedFuture(getClass().getName()
                + " does not support asynchronous execution. To use this tool with an AI Service method"
                + " returning a CompletableFuture, override ToolExecutor.executeAsync()."
                + " If blocking execution is acceptable, it can simply return"
                + " CompletableFuture.completedFuture(executeWithContext(request, context)).");
    }
}
