package dev.langchain4j.reasoning;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Comparator.comparingDouble;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.RelevanceScore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An in-memory implementation of {@link ReasoningBank}.
 * <p>
 * This implementation stores reasoning strategies in memory using a thread-safe list
 * and performs brute-force similarity search using cosine similarity.
 * <p>
 * Suitable for development, testing, and scenarios with a moderate number of strategies.
 * For production use with large numbers of strategies, consider using an embedding store-backed
 * implementation.
 *
 * @since 1.11.0
 */
@Experimental
public class InMemoryReasoningBank implements ReasoningBank {

    private final CopyOnWriteArrayList<Entry> entries;

    /**
     * Creates a new empty in-memory reasoning bank.
     */
    public InMemoryReasoningBank() {
        this.entries = new CopyOnWriteArrayList<>();
    }

    /**
     * Creates an in-memory reasoning bank with existing entries.
     *
     * @param entries Initial entries to populate the bank.
     */
    private InMemoryReasoningBank(Collection<Entry> entries) {
        this.entries = new CopyOnWriteArrayList<>(entries);
    }

    @Override
    public String store(ReasoningStrategy strategy, Embedding embedding) {
        ensureNotNull(strategy, "strategy");
        ensureNotNull(embedding, "embedding");

        String id = randomUUID();
        entries.add(new Entry(id, strategy, embedding));
        return id;
    }

    @Override
    public List<String> storeAll(List<ReasoningStrategy> strategies, List<Embedding> embeddings) {
        if (strategies.size() != embeddings.size()) {
            throw new IllegalArgumentException("The list of strategies and embeddings must have the same size");
        }

        List<String> ids = new ArrayList<>(strategies.size());
        List<Entry> newEntries = new ArrayList<>(strategies.size());

        for (int i = 0; i < strategies.size(); i++) {
            String id = randomUUID();
            ids.add(id);
            newEntries.add(new Entry(id, strategies.get(i), embeddings.get(i)));
        }

        entries.addAll(newEntries);
        return ids;
    }

    @Override
    public ReasoningRetrievalResult retrieve(ReasoningRetrievalRequest request) {
        ensureNotNull(request, "request");

        Comparator<ReasoningMatch> comparator = comparingDouble(ReasoningMatch::score);
        PriorityQueue<ReasoningMatch> matches = new PriorityQueue<>(comparator);

        for (Entry entry : entries) {
            double cosineSimilarity = CosineSimilarity.between(entry.embedding, request.queryEmbedding());
            double score = RelevanceScore.fromCosineSimilarity(cosineSimilarity);

            if (score >= request.minScore()) {
                // Also consider the strategy's own confidence score
                double combinedScore = score * entry.strategy.confidenceScore();
                matches.add(new ReasoningMatch(entry.id, entry.strategy, entry.embedding, combinedScore));

                if (matches.size() > request.maxResults()) {
                    matches.poll();
                }
            }
        }

        List<ReasoningMatch> result = new ArrayList<>(matches);
        result.sort(comparator);
        Collections.reverse(result);

        return ReasoningRetrievalResult.from(result);
    }

    @Override
    public void remove(String id) {
        ensureNotBlank(id, "id");
        entries.removeIf(entry -> entry.id.equals(id));
    }

    /**
     * Removes all strategies with the given IDs.
     *
     * @param ids The IDs of strategies to remove.
     */
    public void removeAll(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Set<String> idSet = (ids instanceof Set) ? (Set<String>) ids : new HashSet<>(ids);
        entries.removeIf(entry -> idSet.contains(entry.id));
    }

    @Override
    public void clear() {
        entries.clear();
    }

    @Override
    public int size() {
        return entries.size();
    }

    /**
     * Returns all entries in the bank.
     * <p>
     * Primarily for debugging and testing purposes.
     *
     * @return An unmodifiable view of all entries.
     */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Creates a new builder for constructing InMemoryReasoningBank instances.
     *
     * @return A new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * An entry in the reasoning bank.
     */
    public static class Entry {

        private final String id;
        private final ReasoningStrategy strategy;
        private final Embedding embedding;

        Entry(String id, ReasoningStrategy strategy, Embedding embedding) {
            this.id = id;
            this.strategy = strategy;
            this.embedding = embedding;
        }

        public String id() {
            return id;
        }

        public ReasoningStrategy strategy() {
            return strategy;
        }

        public Embedding embedding() {
            return embedding;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry entry = (Entry) o;
            return Objects.equals(id, entry.id)
                    && Objects.equals(strategy, entry.strategy)
                    && Objects.equals(embedding, entry.embedding);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, strategy, embedding);
        }
    }

    /**
     * Builder for InMemoryReasoningBank.
     */
    public static class Builder {

        private List<Entry> initialEntries;

        public Builder initialEntries(List<Entry> entries) {
            this.initialEntries = entries;
            return this;
        }

        public InMemoryReasoningBank build() {
            if (initialEntries != null && !initialEntries.isEmpty()) {
                return new InMemoryReasoningBank(initialEntries);
            }
            return new InMemoryReasoningBank();
        }
    }
}
