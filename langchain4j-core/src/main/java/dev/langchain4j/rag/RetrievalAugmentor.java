package dev.langchain4j.rag;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.DefaultExecutorProvider;
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
     * Unlike the leaf RAG SPIs (whose async defaults throw), the default implementation here offloads the blocking
     * {@link #augment(AugmentationRequest)} to a shared virtual-thread executor: the whole RAG flow is a coarse,
     * self-contained operation, so blocking a <i>virtual</i> thread (which unmounts from its carrier while it waits)
     * is non-pinning and lets every existing {@link RetrievalAugmentor} be used from the non-blocking modes without
     * changes. An augmentor that can compose its steps into a genuine future (like {@link DefaultRetrievalAugmentor})
     * should override this to avoid parking a thread at all.
     * <p>
     * An implementation that honors cancellation should abort in-flight work when the returned future is cancelled
     * (best-effort); the offloading default cannot interrupt the running {@link #augment(AugmentationRequest)} call.
     *
     * @param augmentationRequest The {@code AugmentationRequest} containing the {@code ChatMessage} to augment.
     * @return A {@link CompletableFuture} of the {@link AugmentationResult} containing the augmented {@code ChatMessage}.
     * @since 1.17.0
     */
    default CompletableFuture<AugmentationResult> augmentAsync(AugmentationRequest augmentationRequest) {
        return CompletableFuture.supplyAsync(
                () -> augment(augmentationRequest), DefaultExecutorProvider.getDefaultExecutorService());
    }
}
