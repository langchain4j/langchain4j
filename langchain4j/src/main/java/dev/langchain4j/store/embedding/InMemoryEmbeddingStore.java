package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;

import java.util.*;

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
        String id = generateRandomId();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        add(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, Embedded embedded) {
        String id = generateRandomId();
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
    public List<EmbeddingMatch<Embedded>> findRelevant(Embedding referenceEmbedding, int maxResults) {
        return findRelevant(referenceEmbedding, maxResults, -1);
    }

    @Override
    public List<EmbeddingMatch<Embedded>> findRelevant(Embedding referenceEmbedding, int maxResults, double minSimilarity) {

        Comparator<EmbeddingMatch<Embedded>> comparator = comparingDouble(EmbeddingMatch::score);
        PriorityQueue<EmbeddingMatch<Embedded>> matches = new PriorityQueue<>(comparator);

        for (Entry<Embedded> entry : entries) {
            double similarity = cosineSimilarity(entry.embedding, referenceEmbedding);
            if (similarity >= minSimilarity) {
                matches.add(new EmbeddingMatch<>(entry.id, entry.embedding, entry.embedded, similarity));
                if (matches.size() > maxResults) {
                    matches.poll();
                }
            }
        }

        List<EmbeddingMatch<Embedded>> result = new ArrayList<>(matches);
        result.sort(comparingDouble(EmbeddingMatch::score));
        return result;
    }

    /**
     * Calculates cosine similarity between two embeddings (vectors)
     *
     * @param first  embedding
     * @param second embedding
     * @return cosine similarity (from -1 to 1)
     */
    private static float cosineSimilarity(Embedding first, Embedding second) {
        float dot = 0.0F;
        float nru = 0.0F;
        float nrv = 0.0F;

        for (int i = 0; i < first.vector().length; ++i) {
            dot += first.vector()[i] * second.vector()[i];
            nru += first.vector()[i] * first.vector()[i];
            nrv += second.vector()[i] * second.vector()[i];
        }

        return dot / (float) (Math.sqrt(nru) * Math.sqrt(nrv));
    }

    private static String generateRandomId() {
        return UUID.randomUUID().toString();
    }
}
