package dev.langchain4j.reasoning;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.List;

/**
 * A memory bank for storing and retrieving {@link ReasoningStrategy} instances.
 * <p>
 * ReasoningBank enables agents to evolve through accumulated reasoning experiences
 * by storing generalizable strategies extracted from past task executions and
 * retrieving relevant strategies when facing similar tasks.
 * <p>
 * This interface is inspired by the ReasoningBank paper which proposes a memory
 * framework that extracts generalizable reasoning strategies from both successful
 * and failed experiences, enabling continuous agent self-improvement.
 *
 * @see ReasoningStrategy
 * @see ReasoningTrace
 * @since 1.11.0
 */
@Experimental
public interface ReasoningBank {

    /**
     * Stores a reasoning strategy in the bank.
     *
     * @param strategy  The strategy to store.
     * @param embedding The embedding representing the strategy's task pattern.
     * @return The ID assigned to the stored strategy.
     */
    String store(ReasoningStrategy strategy, Embedding embedding);

    /**
     * Stores multiple reasoning strategies in the bank.
     *
     * @param strategies The strategies to store.
     * @param embeddings The embeddings corresponding to each strategy's task pattern.
     * @return The IDs assigned to the stored strategies.
     */
    List<String> storeAll(List<ReasoningStrategy> strategies, List<Embedding> embeddings);

    /**
     * Retrieves the most relevant reasoning strategies for a given task.
     *
     * @param request The retrieval request containing the query embedding and parameters.
     * @return The retrieval result containing matching strategies with their scores.
     */
    ReasoningRetrievalResult retrieve(ReasoningRetrievalRequest request);

    /**
     * Retrieves strategies similar to the given query embedding.
     * <p>
     * Convenience method that creates a request with default parameters.
     *
     * @param queryEmbedding The embedding of the current task.
     * @param maxResults     Maximum number of strategies to retrieve.
     * @return The retrieval result.
     */
    default ReasoningRetrievalResult retrieve(Embedding queryEmbedding, int maxResults) {
        return retrieve(ReasoningRetrievalRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .build());
    }

    /**
     * Removes a strategy from the bank.
     *
     * @param id The ID of the strategy to remove.
     */
    void remove(String id);

    /**
     * Removes all strategies from the bank.
     */
    void clear();

    /**
     * Returns the number of strategies stored in the bank.
     *
     * @return The number of strategies.
     */
    int size();

    /**
     * Checks if the bank is empty.
     *
     * @return true if empty, false otherwise.
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Records a reasoning trace and optionally distills it into strategies.
     * <p>
     * Implementations may immediately distill the trace into a strategy,
     * batch traces for periodic distillation, or delegate to a separate
     * {@link ReasoningDistiller}.
     *
     * @param trace          The reasoning trace to record.
     * @param embeddingModel The embedding model to use for creating embeddings.
     */
    default void recordTrace(ReasoningTrace trace, EmbeddingModel embeddingModel) {
        // Default implementation creates a simple strategy from the trace
        if (trace.isSuccessful() && trace.thinking() != null) {
            ReasoningStrategy strategy = ReasoningStrategy.builder()
                    .taskPattern(trace.taskDescription())
                    .strategy(trace.thinking())
                    .confidenceScore(0.5)
                    .metadata(trace.metadata())
                    .build();

            Embedding embedding = embeddingModel.embed(trace.taskDescription()).content();
            store(strategy, embedding);
        }
    }
}
