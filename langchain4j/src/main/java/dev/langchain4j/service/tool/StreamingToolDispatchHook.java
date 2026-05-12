package dev.langchain4j.service.tool;

import dev.langchain4j.Experimental;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Integration SPI that lets framework integrators (e.g. {@code quarkus-langchain4j}) control how
 * the streaming response handler continues from a tool batch.
 *
 * <p>The streaming handler invokes {@link #dispatch(Supplier)} when it is ready to gather results
 * and continue from the batch of tool calls produced by the LLM. The default implementation runs
 * the supplied work inline on the calling thread and returns a completed {@link CompletionStage}.
 * Frameworks can override this to:
 * <ul>
 *   <li>Switch result gathering, memory writes, follow-up streaming inference, and inline
 *       no-executor tool execution to a worker thread.</li>
 *   <li>Propagate framework context (e.g. Vert.x duplicated context, MDC, security context)
 *       through that downstream continuation.</li>
 *   <li>Hook downstream cancellation into framework cancellation.</li>
 *   <li>Control which thread the next inference request and the emission of subsequent stream
 *       events are scheduled on.</li>
 * </ul>
 *
 * <p>The supplied {@link Supplier} performs the downstream side-effect chain: tool dispatch or
 * result gathering, memory writes, follow-up streaming inference request, and event emissions.
 * With the default unlimited {@code maxToolCallsPerResponse} setting, a configured per-tool
 * {@link java.util.concurrent.Executor} causes streaming tools to be scheduled eagerly from
 * {@code onCompleteToolCall} before this hook is invoked, so the hook cannot switch threads before
 * those tool executions begin. When no per-tool executor is configured, or when
 * {@code maxToolCallsPerResponse} is greater than {@code 0}, the supplier executes the deferred
 * batch dispatch after {@code onCompleteResponse}. The hook should avoid blocking on the resulting
 * {@link CompletionStage}, but must ensure that any thread-context required by downstream code is
 * established before invoking the supplier.
 *
 * <p>The handler does not currently use the returned {@link CompletionStage} for back-pressure
 * or chaining; it is returned for diagnostic and testing purposes.
 *
 * @since 1.15.0-beta25
 */
@Experimental
@FunctionalInterface
public interface StreamingToolDispatchHook {

    /**
     * Invokes {@code work} according to the framework's preferred scheduling/dispatch policy.
     *
     * <p>Implementations must call {@code work.get()} exactly once. Returning without invoking
     * {@code work} will silently drop result gathering, memory writes, and follow-up inference.
     * When dispatch is deferred, this also means the tool batch is not executed. In default
     * unlimited mode with a per-tool executor, tools may already have been scheduled eagerly before
     * this hook is invoked.
     *
     * @param work the encapsulated downstream work; running it gathers tool results, writes memory,
     *             and schedules the follow-up streaming request. When dispatch is deferred, it also
     *             executes the tool batch.
     * @return a {@link CompletionStage} that completes when the supplied work has finished
     *         (or completes exceptionally if it throws). The default implementation runs the
     *         supplier inline and returns an already-completed stage.
     */
    <T> CompletionStage<T> dispatch(Supplier<T> work);

    /**
     * No-op hook that runs the supplied work on the calling thread. This is the default used
     * when no hook is configured.
     */
    StreamingToolDispatchHook INLINE = new StreamingToolDispatchHook() {
        @Override
        public <T> CompletionStage<T> dispatch(Supplier<T> work) {
            try {
                return CompletableFuture.completedFuture(work.get());
            } catch (Throwable t) {
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(t);
                return failed;
            }
        }
    };
}
