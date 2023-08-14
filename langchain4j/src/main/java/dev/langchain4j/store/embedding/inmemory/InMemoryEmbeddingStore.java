package dev.langchain4j.store.embedding.inmemory;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;

import java.util.*;

import static dev.langchain4j.internal.Utils.randomUUID;
import static java.util.Comparator.comparingDouble;

public class InMemoryEmbeddingStore<Embedded> implements EmbeddingStore<Embedded> {

    private static class Entry<Embedded> {

        String id;
        Embedding embedding;
        Embedded embedded;

        Entry(String id, Embedding embedding, Embedded embedded) {
            this.id = id;
            this.embedding = embedding;
            this.embedded = embedded;
        }
    }

    private final List<Entry<Embedded>> entries = new ArrayList<>();

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        add(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, Embedded embedded) {
        String id = randomUUID();
        add(id, embedding, embedded);
        return id;
    }

    private void add(String id, Embedding embedding, Embedded embedded) {
        entries.add(new Entry<>(id, embedding, embedded));
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = new ArrayList<>();
        for (Embedding embedding : embeddings) {
            ids.add(add(embedding));
        }
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<Embedded> embedded) {
        if (embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("The list of embeddings and embedded must have the same size");
        }

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            ids.add(add(embeddings.get(i), embedded.get(i)));
        }
        return ids;
    }

    @Override
    public List<EmbeddingMatch<Embedded>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {

        Comparator<EmbeddingMatch<Embedded>> comparator = comparingDouble(EmbeddingMatch::score);
        PriorityQueue<EmbeddingMatch<Embedded>> matches = new PriorityQueue<>(comparator);

        for (Entry<Embedded> entry : entries) {
            double score = RelevanceScore.cosine(entry.embedding.vector(), referenceEmbedding.vector());
            if (score >= minScore) {
                matches.add(new EmbeddingMatch<>(score, entry.id, entry.embedding, entry.embedded));
                if (matches.size() > maxResults) {
                    matches.poll();
                }
            }
        }

        List<EmbeddingMatch<Embedded>> result = new ArrayList<>(matches);
        result.sort(comparator);
        Collections.reverse(result);
        return result;
    }
}
