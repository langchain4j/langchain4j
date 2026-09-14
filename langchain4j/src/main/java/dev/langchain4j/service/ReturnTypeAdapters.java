package dev.langchain4j.service;

import static dev.langchain4j.service.TypeUtils.typeHasRawClass;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.spi.services.CompletableFutureAdapter;
import dev.langchain4j.spi.services.PublisherAdapter;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * The adapters that let an AI Service method return an asynchronous or reactive type of a third-party
 * library, such as Mutiny {@code Uni} or Reactor {@code Mono} and {@code Flux}.
 * <p>
 * Loaded once: the answer cannot change while the application is running, and every AI Service asks
 * the same question.
 */
class ReturnTypeAdapters {

    private static final Collection<CompletableFutureAdapter> COMPLETABLE_FUTURE_ADAPTERS =
            loadFactories(CompletableFutureAdapter.class);
    private static final Collection<PublisherAdapter> PUBLISHER_ADAPTERS = loadFactories(PublisherAdapter.class);

    private ReturnTypeAdapters() {}

    static CompletableFutureAdapter findCompletableFutureAdapter(Type returnType) {
        for (CompletableFutureAdapter adapter : COMPLETABLE_FUTURE_ADAPTERS) {
            if (adapter.canAdapt(returnType)) {
                return adapter;
            }
        }
        return null;
    }

    static PublisherAdapter findPublisherAdapter(Type returnType) {
        for (PublisherAdapter adapter : PUBLISHER_ADAPTERS) {
            if (adapter.canAdapt(returnType)) {
                return adapter;
            }
        }
        return null;
    }

    /**
     * Whether a method with this return type is served by the asynchronous or the reactive path,
     * rather than by the blocking one.
     */
    static boolean isAsynchronousOrReactive(Type returnType) {
        return typeHasRawClass(returnType, CompletableFuture.class)
                || typeHasRawClass(returnType, CompletionStage.class)
                || typeHasRawClass(returnType, Flow.Publisher.class)
                || findCompletableFutureAdapter(returnType) != null
                || findPublisherAdapter(returnType) != null;
    }
}
