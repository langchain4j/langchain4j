package dev.langchain4j.store.embedding.inmemory;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.spi.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodecFactory;
import dev.langchain4j.store.embedding.*;
import dev.langchain4j.store.embedding.filter.Filter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toList;

/**
 * An {@link EmbeddingStore} that stores embeddings in memory.
 * <p>
 * Uses a brute force approach by iterating over all embeddings to find the best matches.
 * <p>
 * This store can be persisted using the {@link #serializeToJson()} and {@link #serializeToFile(Path)} methods.
 * <p>
 * It can also be recreated from JSON or a file using the {@link #fromJson(String)} and {@link #fromFile(Path)} methods.
 *
 * @param <Embedded> The class of the object that has been embedded.
 *                   Typically, it is {@link dev.langchain4j.data.segment.TextSegment}.
 */
public class InMemoryEmbeddingStore<Embedded> implements EmbeddingStore<Embedded> {

    final CopyOnWriteArrayList<Entry<Embedded>> entries = new CopyOnWriteArrayList<>();

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

    public void add(String id, Embedding embedding, Embedded embedded) {
        entries.add(new Entry<>(id, embedding, embedded));
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {

        List<Entry<Embedded>> newEntries = embeddings.stream()
                .map(embedding -> new Entry<Embedded>(randomUUID(), embedding))
                .collect(toList());

        return add(newEntries);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<Embedded> embedded) {
        if (embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("The list of embeddings and embedded must have the same size");
        }

        List<Entry<Embedded>> newEntries = IntStream.range(0, embeddings.size())
                .mapToObj(i -> new Entry<>(randomUUID(), embeddings.get(i), embedded.get(i)))
                .collect(toList());

        return add(newEntries);
    }

    private List<String> add(List<Entry<Embedded>> newEntries) {

        entries.addAll(newEntries);

        return newEntries.stream()
                .map(entry -> entry.id)
                .collect(toList());
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");

        entries.removeIf(entry -> ids.contains(entry.id));
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");

        entries.removeIf(entry -> {
            if (entry.embedded instanceof TextSegment) {
                return filter.test(((TextSegment) entry.embedded).metadata());
            } else if (entry.embedded == null) {
                return false;
            } else {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        });
    }

    @Override
    public void removeAll() {
        entries.clear();
    }

    @Override
    public EmbeddingSearchResult<Embedded> search(EmbeddingSearchRequest embeddingSearchRequest) {

        Comparator<EmbeddingMatch<Embedded>> comparator = comparingDouble(EmbeddingMatch::score);
        PriorityQueue<EmbeddingMatch<Embedded>> matches = new PriorityQueue<>(comparator);

        Filter filter = embeddingSearchRequest.filter();

        for (Entry<Embedded> entry : entries) {

            if (filter != null && entry.embedded instanceof TextSegment) {
                Metadata metadata = ((TextSegment) entry.embedded).metadata();
                if (!filter.test(metadata)) {
                    continue;
                }
            }

            double cosineSimilarity = CosineSimilarity.between(entry.embedding, embeddingSearchRequest.queryEmbedding());
            double score = RelevanceScore.fromCosineSimilarity(cosineSimilarity);
            if (score >= embeddingSearchRequest.minScore()) {
                matches.add(new EmbeddingMatch<>(score, entry.id, entry.embedding, entry.embedded));
                if (matches.size() > embeddingSearchRequest.maxResults()) {
                    matches.poll();
                }
            }
        }

        List<EmbeddingMatch<Embedded>> result = new ArrayList<>(matches);
        result.sort(comparator);
        Collections.reverse(result);

        return new EmbeddingSearchResult<>(result);
    }

    public String serializeToJson() {
        return loadCodec().toJson(this);
    }

    public void serializeToFile(Path filePath) {
        try {
            String json = serializeToJson();
            Files.write(filePath, json.getBytes(), CREATE, TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void serializeToFile(String filePath) {
        serializeToFile(Paths.get(filePath));
    }

    public static InMemoryEmbeddingStore<TextSegment> fromJson(String json) {
        return loadCodec().fromJson(json);
    }

    public static InMemoryEmbeddingStore<TextSegment> fromFile(Path filePath) {
        try {
            String json = new String(Files.readAllBytes(filePath));
            return fromJson(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static InMemoryEmbeddingStore<TextSegment> fromFile(String filePath) {
        return fromFile(Paths.get(filePath));
    }

    private static class Entry<Embedded> {

        String id;
        Embedding embedding;
        Embedded embedded;

        Entry(String id, Embedding embedding) {
            this(id, embedding, null);
        }

        Entry(String id, Embedding embedding, Embedded embedded) {
            this.id = ensureNotBlank(id, "id");
            this.embedding = ensureNotNull(embedding, "embedding");
            this.embedded = embedded;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry<?> that = (Entry<?>) o;
            return Objects.equals(this.id, that.id)
                    && Objects.equals(this.embedding, that.embedding)
                    && Objects.equals(this.embedded, that.embedded);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, embedding, embedded);
        }
    }

    private static InMemoryEmbeddingStoreJsonCodec loadCodec() {
        for (InMemoryEmbeddingStoreJsonCodecFactory factory : loadFactories(InMemoryEmbeddingStoreJsonCodecFactory.class)) {
            return factory.create();
        }
        return new GsonInMemoryEmbeddingStoreJsonCodec();
    }
}
