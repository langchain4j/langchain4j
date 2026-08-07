package dev.langchain4j.context;

import dev.langchain4j.Experimental;
import dev.langchain4j.rag.content.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * Default implementation of {@link ContextManager} that composes multiple {@link ContextProvider}s.
 * <p>
 * Providers are called sequentially by default.
 * If an {@link Executor} is configured, providers are called in parallel.
 * <p>
 * Failed providers are logged and skipped (graceful degradation).
 *
 * @see ContextManager
 * @see ContextProvider
 */
@Experimental
public class DefaultContextManager implements ContextManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextManager.class);

    private final List<ContextProvider> providers;
    private final Executor executor;

    private DefaultContextManager(List<ContextProvider> providers, Executor executor) {
        this.providers = copy(ensureNotEmpty(providers, "providers"));
        this.executor = executor;
    }

    @Override
    public ContextResult resolveContext(ContextRequest request) {
        List<Content> allContents;
        if (executor != null && providers.size() > 1) {
            allContents = resolveInParallel(request);
        } else {
            allContents = resolveSequentially(request);
        }
        return ContextResult.from(allContents);
    }

    private List<Content> resolveSequentially(ContextRequest request) {
        List<Content> allContents = new ArrayList<>();
        for (ContextProvider provider : providers) {
            try {
                List<Content> contents = provider.provideContext(request);
                if (contents != null) {
                    allContents.addAll(contents);
                }
            } catch (Exception e) {
                log.warn("ContextProvider '{}' threw an exception, skipping", provider.name(), e);
            }
        }
        return allContents;
    }

    private List<Content> resolveInParallel(ContextRequest request) {
        List<CompletableFuture<List<Content>>> futures = new ArrayList<>(providers.size());
        for (ContextProvider provider : providers) {
            futures.add(supplyAsync(() -> {
                try {
                    List<Content> contents = provider.provideContext(request);
                    return getOrDefault(contents, List.of());
                } catch (Exception e) {
                    log.warn("ContextProvider '{}' threw an exception, skipping", provider.name(), e);
                    return List.of();
                }
            }, executor));
        }

        allOf(futures.toArray(new CompletableFuture[0])).join();

        List<Content> allContents = new ArrayList<>();
        for (CompletableFuture<List<Content>> future : futures) {
            allContents.addAll(future.join());
        }
        return allContents;
    }

    public static DefaultContextManagerBuilder builder() {
        return new DefaultContextManagerBuilder();
    }

    public static class DefaultContextManagerBuilder {

        private final List<ContextProvider> providers = new ArrayList<>();
        private Executor executor;

        DefaultContextManagerBuilder() {
        }

        public DefaultContextManagerBuilder contextProvider(ContextProvider contextProvider) {
            this.providers.add(ensureNotNull(contextProvider, "contextProvider"));
            return this;
        }

        public DefaultContextManagerBuilder contextProviders(List<ContextProvider> contextProviders) {
            this.providers.addAll(ensureNotEmpty(contextProviders, "contextProviders"));
            return this;
        }

        public DefaultContextManagerBuilder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        public DefaultContextManager build() {
            return new DefaultContextManager(providers, executor);
        }
    }
}
