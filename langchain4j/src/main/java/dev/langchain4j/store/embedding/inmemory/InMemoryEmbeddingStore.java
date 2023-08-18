package dev.langchain4j.store.embedding.inmemory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static dev.langchain4j.internal.Utils.randomUUID;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
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

        @Override
        public boolean equals(Object o) { // TODO remove
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry that = (Entry) o;
            return Objects.equals(this.id, that.id)
                    && Objects.equals(this.embedding, that.embedding)
                    && Objects.equals(this.embedded, that.embedded);
        }

        @Override
        public int hashCode() { // TODO remove
            return Objects.hash(id, embedding, embedded);
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

    public void toFile(String filePath) { //  TODO better name
        try {
            String json = new Gson().toJson(this);
            Files.write(Paths.get(filePath), json.getBytes(), CREATE, TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static InMemoryEmbeddingStore<TextSegment> fromFile(String filePath) { //  TODO better name
        try {
            String json = new String(Files.readAllBytes(Paths.get(filePath)));
            Type type = new TypeToken<InMemoryEmbeddingStore<TextSegment>>() {
            }.getType();
            return new Gson().fromJson(json, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
