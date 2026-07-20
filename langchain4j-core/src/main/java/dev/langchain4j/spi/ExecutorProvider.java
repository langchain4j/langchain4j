package dev.langchain4j.spi;

import dev.langchain4j.internal.DefaultExecutorProvider;
import java.util.concurrent.Executor;

/**
 * SPI for supplying the {@link Executor} that LangChain4j uses to offload blocking work and run its asynchronous
 * continuations — concurrent tool execution, blocking RAG / retrieval and embedding-store calls, moderation,
 * in-process embedding models, retry backoff scheduling, and similar.
 *
 * <h2>Why implement this</h2>
 * LangChain4j hops threads at these offload points and does <b>not</b> propagate thread-local context
 * (tracing spans, SLF4J MDC, security, CDI / request scope, …) across them by default. Registering an
 * {@code ExecutorProvider} that returns a <em>context-propagating</em> executor makes that context follow the
 * work. Typical wrappers:
 * <ul>
 *   <li>Quarkus / MicroProfile — a {@code ManagedExecutor}</li>
 *   <li>Spring — an {@code Executor} wrapped with a {@code TaskDecorator}, or a {@code ContextExecutorService}</li>
 *   <li>OpenTelemetry — {@code Context.taskWrapping(executor)}</li>
 *   <li>Micrometer — {@code ContextSnapshot.wrap(executor)}</li>
 * </ul>
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
 *   <li>a built-in virtual-thread-based default.</li>
 * </ol>
 * Component-level executors (a retriever's, an AI Service's concurrent-tool executor, a transport's own
 * {@code executor(...)} builder option, …) take precedence over this global default when explicitly set.
 *
 * <h2>Contract</h2>
 * {@link #executor()} is called at each offload, so it must return a <b>shared, long-lived</b> executor rather
 * than create a new one per call.
 *
 * <p>This SPI intentionally exposes a single, global executor. Should per-purpose executors (e.g. separate pools
 * for CPU-bound vs. blocking work) ever be needed, they can be added here as {@code default} methods that fall
 * back to {@link #executor()}, without breaking existing implementations.
 *
 * @since 1.19.0
 */
public interface ExecutorProvider {

    /**
     * @return the shared {@link Executor} LangChain4j should offload blocking work and asynchronous
     *         continuations onto. Must not be {@code null}.
     */
    Executor executor();

    /**
     * Registers a process-wide {@code ExecutorProvider} programmatically, taking precedence over any
     * {@code ServiceLoader}-discovered provider and the built-in default. This is a convenience for tests and
     * non-DI applications; framework integrations typically register via {@code ServiceLoader} instead.
     *
     * <p>Example — make OpenTelemetry spans and SLF4J MDC follow every LangChain4j offload:
     * <pre>{@code
     * ExecutorService base = Executors.newVirtualThreadPerTaskExecutor();
     * Executor contextAware = Context.taskWrapping(base); // OpenTelemetry
     * ExecutorProvider.set(() -> contextAware);
     * }</pre>
     *
     * @param provider the provider to register, or {@code null} to clear a previously-set one (falling back to
     *                 the {@code ServiceLoader} provider, then the built-in default).
     * @since 1.19.0
     */
    static void set(ExecutorProvider provider) {
        DefaultExecutorProvider.setProvider(provider);
    }

    /**
     * @return the {@code ExecutorProvider} previously registered via {@link #set(ExecutorProvider)}, or
     *         {@code null} if none was set (in which case a {@code ServiceLoader} provider or the built-in default
     *         is in effect). Does not return the {@code ServiceLoader}-discovered provider.
     * @since 1.19.0
     */
    static ExecutorProvider get() {
        return DefaultExecutorProvider.getProvider();
    }
}
