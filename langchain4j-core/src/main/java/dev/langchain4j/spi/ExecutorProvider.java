package dev.langchain4j.spi;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.DefaultExecutorProvider;
import java.util.concurrent.Executor;

/**
 * SPI for supplying the {@link Executor} that LangChain4j uses to offload blocking work and run its asynchronous
 * continuations — concurrent tool execution, blocking RAG / retrieval and embedding-store calls, moderation,
 * in-process embedding models, retry backoff scheduling, and similar.
 *
 * <h2>Why implement this</h2>
 * LangChain4j hops threads at these offload points and does <b>not</b> propagate thread-local context
 * (tracing spans, SLF4J MDC, security, CDI / request scope, …) across them by default. To make the context of the
 * thread that calls an AI Service follow the work that the call offloads, implement {@link #captureContext()}.
 * <p>
 * A <em>context-propagating</em> executor (a MicroProfile {@code ManagedExecutor}, a Spring {@code TaskDecorator},
 * OpenTelemetry {@code Context.taskWrapping(executor)}, Micrometer {@code ContextSnapshot.wrap(executor)}, …) is
 * not enough on its own: it captures the context of the thread that <em>submits</em> a task, and in a
 * non-blocking AI Service call most work is submitted after the model has answered, from the thread that delivered
 * the answer. Both can be combined: a task then runs with the context captured by {@link #captureContext()},
 * applied inside whatever the executor sets up.
 *
 * <h2>{@code Executor}, not {@code ExecutorService}</h2>
 * The return type is the minimal {@link Executor}: LangChain4j only needs to <em>run</em> tasks and never owns
 * the executor's lifecycle (it will not call {@code shutdown()}). This accepts the widest range of host
 * executors — every managed / context-propagating pool (Quarkus {@code ManagedExecutor}, Jakarta
 * {@code ManagedExecutorService}, Guava {@code ListeningExecutorService}, a Spring {@code TaskExecutor}, …) is
 * an {@code Executor}. Every offload point is driven through this single {@code Executor}; components that need a
 * {@link java.util.concurrent.Future} handle (to cancel or time-bound a task) obtain it via
 * {@code CompletableFuture.supplyAsync(..., executor)} rather than requiring an {@code ExecutorService}.
 *
 * <h2>Discovery and precedence</h2>
 * A provider is registered either by implementing this interface and declaring it for {@link
 * java.util.ServiceLoader} (the standard way; frameworks typically do this for you), or programmatically via
 * {@link #set(ExecutorProvider)} (a convenience for tests and non-DI applications). The effective executor is
 * resolved as:
 * <ol>
 *   <li>a provider set programmatically via {@link #set(ExecutorProvider)};</li>
 *   <li>the first {@code ExecutorProvider} found on the classpath via {@code ServiceLoader};</li>
 *   <li>a built-in default: a virtual-thread-per-task executor on Java 21 or later, falling back to an
 *       unbounded platform-thread pool on Java 17-20.</li>
 * </ol>
 * Component-level executors (a retriever's, an AI Service's concurrent-tool executor, a transport's own
 * {@code executor(...)} builder option, …) take precedence over this global default when explicitly set.
 *
 * <h2>Contract</h2>
 * {@link #executor()} is called at each offload, so it must return a <b>shared, long-lived</b> executor rather
 * than create a new one per call. {@link #captureContext()} has its own rules, see there.
 *
 * <p>This SPI intentionally exposes a single, global executor. Should per-purpose executors (e.g. separate pools
 * for CPU-bound vs. blocking work) ever be needed, they can be added here as {@code default} methods that fall
 * back to {@link #executor()}, without breaking existing implementations.
 *
 * @since 1.20.0
 */
@Experimental
public interface ExecutorProvider {

    /**
     * @return the shared {@link Executor} LangChain4j should offload blocking work and asynchronous
     *         continuations onto, or {@code null} to keep the built-in default (for a provider that only
     *         implements {@link #captureContext()}).
     */
    Executor executor();

    /**
     * Captures the context of the calling thread (tracing span, SLF4J MDC, security context, …), so that it can be
     * restored in tasks that are submitted later, possibly from another thread.
     * <p>
     * LangChain4j calls this method once per AI Service invocation, in every mode, on the thread that calls the AI
     * Service method (for methods returning a {@code Flow.Publisher}, {@code Mono} or {@code Flux}: when the method
     * is called, not when the result is subscribed). It applies the returned {@link CapturedContext} to the tasks that the
     * invocation offloads to an executor (tools, blocking RAG stages, moderation), whichever thread submits them.
     * Work that runs without being offloaded (guardrails, chat memory, listeners, streaming callbacks) is not
     * wrapped, and neither is work offloaded outside an AI Service invocation (retry backoff, threads that a model
     * integration starts on its own, parallel and asynchronous agents of {@code langchain4j-agentic}).
     * <p>
     * This applies to AI Services created with {@code AiServices}. A framework that builds its own AI Service
     * implementation decides itself how it propagates context.
     * <p>
     * Implementations must follow these rules:
     * <ul>
     *   <li>This method is called on every invocation, possibly on an event-loop thread: it must be cheap and must
     *       not block.</li>
     *   <li>The returned {@link CapturedContext} may wrap several tasks that run concurrently, and may be kept for
     *       as long as the invocation: it must not hold per-task state.</li>
     *   <li>A wrapped task must restore the previous context of the thread it runs on when it completes, normally
     *       or exceptionally. Tasks run on pooled threads, so a context left behind leaks into unrelated tasks,
     *       including another user's security context.</li>
     * </ul>
     * For example, with Micrometer context propagation, whose {@code ContextSnapshot} follows these rules:
     * <pre>{@code
     * private static final ContextSnapshotFactory CONTEXT_SNAPSHOT_FACTORY = ContextSnapshotFactory.builder().build();
     *
     * public CapturedContext captureContext() {
     *     return CONTEXT_SNAPSHOT_FACTORY.captureAll()::wrap;
     * }
     * }</pre>
     * The default implementation captures nothing and returns {@link CapturedContext#NONE}.
     *
     * @return the context captured by this call. Must not be {@code null}.
     * @since 1.23.0
     */
    default CapturedContext captureContext() {
        return CapturedContext.NONE;
    }

    /**
     * Registers a process-wide {@code ExecutorProvider} programmatically, taking precedence over any
     * {@code ServiceLoader}-discovered provider and the built-in default. This is a convenience for tests and
     * non-DI applications; framework integrations typically register via {@code ServiceLoader} instead.
     *
     * <p>Example — run LangChain4j offloads on an application executor:
     * <pre>{@code
     * ExecutorProvider.set(() -> applicationExecutor);
     * }</pre>
     * To make the caller's context (tracing span, MDC, …) follow the offloaded work, register a provider that
     * implements {@link #captureContext()}; a lambda cannot.
     *
     * @param provider the provider to register, or {@code null} to clear a previously-set one (falling back to
     *                 the {@code ServiceLoader} provider, then the built-in default).
     * @since 1.20.0
     */
    static void set(ExecutorProvider provider) {
        DefaultExecutorProvider.setProvider(provider);
    }

    /**
     * @return the {@code ExecutorProvider} previously registered via {@link #set(ExecutorProvider)}, or
     *         {@code null} if none was set (in which case a {@code ServiceLoader} provider or the built-in default
     *         is in effect). Does not return the {@code ServiceLoader}-discovered provider.
     * @since 1.20.0
     */
    static ExecutorProvider get() {
        return DefaultExecutorProvider.getProvider();
    }
}
