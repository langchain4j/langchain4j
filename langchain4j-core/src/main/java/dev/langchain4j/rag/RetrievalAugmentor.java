package dev.langchain4j.rag;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.internal.AsyncNotSupported;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.rag.content.Content;

import java.util.concurrent.CompletableFuture;

/**
 * Augments the provided {@link ChatMessage} with retrieved {@link Content}s.
 * <br>
 * This serves as an entry point into the RAG flow in LangChain4j.
 * <br>
 * You are free to use the default implementation ({@link DefaultRetrievalAugmentor}) or to implement a custom one.
 *
 * @see DefaultRetrievalAugmentor
 */
public interface RetrievalAugmentor {

    /**
     * Augments the {@link ChatMessage} provided in the {@link AugmentationRequest} with retrieved {@link Content}s.
     *
     * @param augmentationRequest The {@code AugmentationRequest} containing the {@code ChatMessage} to augment.
     * @return The {@link AugmentationResult} containing the augmented {@code ChatMessage}.
     */
    AugmentationResult augment(AugmentationRequest augmentationRequest);

    /**
     * Non-blocking counterpart of {@link #augment(AugmentationRequest)}, invoked by the asynchronous
     * ({@link java.util.concurrent.CompletableFuture}/{@link java.util.concurrent.CompletionStage}) and reactive
     * ({@link java.util.concurrent.Flow.Publisher}) AI Service modes so the RAG flow never blocks the calling thread.
     * <p>
     * The default returns a failed future carrying {@link AsyncNotSupportedException}: an augmentor that is not genuinely asynchronous does
     * not pretend to be. {@link DefaultRetrievalAugmentor} overrides this to compose its steps into a real future. A
     * custom augmentor that has not opted in is still usable from the non-blocking modes: the AI Service offloads its
     * blocking {@link #augment(AugmentationRequest)} to a shared virtual-thread executor, rather than the augmentor
     * being silently offloaded on every call.
     * <p>
     * An implementation that honors cancellation should abort in-flight work when the returned future is cancelled
     * (best-effort).
     *
     * @param augmentationRequest The {@code AugmentationRequest} containing the {@code ChatMessage} to augment.
     * @return A {@link CompletableFuture} of the {@link AugmentationResult} containing the augmented {@code ChatMessage}.
     * @since 1.19.0
     */
    default CompletableFuture<AugmentationResult> augmentAsync(AugmentationRequest augmentationRequest) {
        return AsyncNotSupported.failedFuture(getClass(), "augmentAsync");
    }
}
