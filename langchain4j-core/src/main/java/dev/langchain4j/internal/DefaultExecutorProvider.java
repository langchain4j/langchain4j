package dev.langchain4j.internal;

import static dev.langchain4j.internal.VirtualThreadUtils.createVirtualThreadExecutor;

import dev.langchain4j.Internal;
import dev.langchain4j.spi.ExecutorProvider;
import dev.langchain4j.spi.ServiceHelper;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Resolves the {@link Executor} that LangChain4j uses to offload blocking work and run its asynchronous
 * continuations. Resolution order: a programmatically-set {@link ExecutorProvider} → the first
 * {@link ExecutorProvider} discovered via {@code ServiceLoader} → a built-in virtual-thread default.
 * <p>
 * {@link #getDefaultExecutor()} returns the resolved executor as-is and backs every offload. The legacy
 * {@link #getDefaultExecutorService()} is deprecated (retained only for external back-compat).
 * <p>
 * This is the single seam a host can use to make LangChain4j's internal thread hops carry its context (tracing,
 * MDC, security, …): register an {@link ExecutorProvider} returning a context-propagating executor. With no
 * provider registered, the behavior is unchanged — a shared virtual-thread executor is used.
 */
@Internal
public class DefaultExecutorProvider {

    private DefaultExecutorProvider() {}

    private static volatile ExecutorProvider programmaticProvider;

    /**
     * Internal state mutator behind the public {@link ExecutorProvider#set(ExecutorProvider)}. A
     * programmatically-set provider takes precedence over any {@code ServiceLoader}-discovered provider and the
     * built-in default. Pass {@code null} to clear it.
     */
    public static void setProvider(ExecutorProvider provider) {
        programmaticProvider = provider;
    }

    /**
     * Internal accessor behind the public {@link ExecutorProvider#get()}.
     *
     * @return the programmatically-set provider, or {@code null} if none was set (a {@code ServiceLoader} provider
     *         or the built-in default may still be in effect).
     */
    public static ExecutorProvider getProvider() {
        return programmaticProvider;
    }

    /**
     * @return the resolved {@link Executor} (programmatic override → {@code ServiceLoader} SPI → built-in
     *         virtual-thread default). This is the seam that honors any host-registered {@link ExecutorProvider},
     *         including a bare {@link Executor}.
     */
    public static Executor getDefaultExecutor() {
        Executor executor = resolveProvidedExecutor();
        return executor != null ? executor : BuiltInHolder.EXECUTOR_SERVICE;
    }

    /**
     * @return an {@link ExecutorService} — the registered provider's executor if it is itself an
     *         {@code ExecutorService} (as most managed pools are), otherwise the built-in default.
     * @deprecated LangChain4j now offloads everything through {@link #getDefaultExecutor()} (a plain
     *         {@link Executor}); no internal site needs an {@code ExecutorService} anymore. Retained only for
     *         backward compatibility with external callers. Prefer {@link #getDefaultExecutor()}.
     */
    @Deprecated(since = "1.19.0")
    public static ExecutorService getDefaultExecutorService() {
        Executor executor = resolveProvidedExecutor();
        if (executor instanceof ExecutorService executorService) {
            return executorService;
        }
        return BuiltInHolder.EXECUTOR_SERVICE;
    }

    private static Executor resolveProvidedExecutor() {
        ExecutorProvider provider = programmaticProvider;
        if (provider == null) {
            provider = SpiHolder.PROVIDER;
        }
        return provider != null ? provider.executor() : null;
    }

    private static final class SpiHolder {
        private static final ExecutorProvider PROVIDER = ServiceHelper.loadFactory(ExecutorProvider.class);
    }

    private static final class BuiltInHolder {
        private static final ExecutorService EXECUTOR_SERVICE =
                createVirtualThreadExecutor(BuiltInHolder::createPlatformThreadExecutorService);

        private static ExecutorService createPlatformThreadExecutorService() {
            return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 1, TimeUnit.SECONDS, new SynchronousQueue<>());
        }
    }
}
