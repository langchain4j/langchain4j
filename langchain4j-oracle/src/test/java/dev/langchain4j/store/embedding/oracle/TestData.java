package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Assertions;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.randomFloats;

/**
 * An object representation of the data stored by {@link OracleEmbeddingStore}. This class can be
 * used to generate test data. It implements {@link #equals(Object)}, {@link #toString()}, and {@link #hashCode()} so
 * that results may be verified with {@link Assertions#assertEquals(Object, Object)}
 */
final class TestData {
    final String id;
    final Embedding embedding;
    final TextSegment textSegment;

    /** Create data with a random id, embedding, and text segment */
    public TestData() {
        this(randomEmbedding());
    }

    /** Create data with a random id and text segment */
    public TestData(Embedding embedding) {
        this(randomId(), embedding, randomTextSegment());
    }

    /** Create data with a random id */
    public TestData(Embedding embedding, TextSegment textSegment) {
       this(UUID.randomUUID().toString(), embedding, textSegment);
    }

    /** Create data with the same id, embedding, and text segment as an embedding match */
    public TestData(EmbeddingMatch<TextSegment> embeddingMatch) {
        this(embeddingMatch.embeddingId(), embeddingMatch.embedding(), embeddingMatch.embedded());
    }

    /** Create data with the given id, embedding, and textSegment. All mutable objects are copied. */
    public TestData(String id, Embedding embedding, TextSegment textSegment) {
        this.id = id;
        this.embedding = embedding == null
                ? null
                : Embedding.from(embedding.vector());
        this.textSegment = textSegment == null
                ? null
                : TextSegment.from(textSegment.text(), textSegment.metadata().copy());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TestData))
            return false;

        TestData testEmbedding = (TestData) other;
        return Objects.equals(id, testEmbedding.id)
                && Objects.equals(embedding, testEmbedding.embedding)
                && Objects.equals(textSegment, testEmbedding.textSegment);
    }

    @Override
    public String toString() {
        return "id=" + id + ", embedding=" + embedding + ", textSegment=" + textSegment;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, embedding, textSegment);
    }

    /** Returns a random embedding of 512 dimensions */
    static Embedding randomEmbedding() {
        return Embedding.from(randomFloats(512));
    }

    /** Returns a random UUID */
    static String randomId() {
        return UUID.randomUUID().toString();
    }

    /** Returns a random text segment with random metadata */
    static TextSegment randomTextSegment() {
        float[] random = randomFloats(3);
        TextSegment textSegment = TextSegment.from("TEST " + random[0]);
        textSegment.metadata()
                .put("x", random[1])
                .put("y", random[2])
                .put("hashCode", textSegment.hashCode())
                .put("timestamp", OffsetDateTime.now().toString());
        return textSegment;
    }

    /**
     * Creates and adds test data with a random embedding, and no text segment.
     *
     * @param embeddingStore Store to add the data to. Not null.
     *
     * @return Test data with a random embedding and no text segment.
     */
    static TestData add(EmbeddingStore<TextSegment> embeddingStore) {
        Embedding embedding = randomEmbedding();
        String id = embeddingStore.add(embedding);
        return new TestData(id, embedding, null);
    }

    /**
     * Creates and adds test data with a random ID, random embedding, and no text segment.
     *
     * @param embeddingStore Store to add the data to. Not null.
     *
     * @return Test data with a random ID, random embedding, and no text segment.
     */
    static TestData addWithId(EmbeddingStore<TextSegment> embeddingStore) {
        String id = randomId();
        Embedding embedding = randomEmbedding();
        embeddingStore.add(id, embedding);
        return new TestData(id, embedding, null);
    }

    /**
     * Creates and adds test data with a random embedding, and random text segment.
     *
     * @param embeddingStore Store to add the data to. Not null.
     *
     * @return Test data with a random embedding, and random text segment.
     */
    static TestData addWithTextSegment(EmbeddingStore<TextSegment> embeddingStore) {
        Embedding embedding = randomEmbedding();
        TextSegment textSegment = randomTextSegment();
        String id = embeddingStore.add(embedding, textSegment);
        return new TestData(id, embedding, textSegment);
    }

    /**
     * Creates and adds 100 rows of test data with random embeddings, and no text segment.
     *
     * @param embeddingStore Store to add the data to. Not null.
     *
     * @return Test data with a random embeddings, and no text segments.
     */
    static List<TestData> addAll(EmbeddingStore<TextSegment> embeddingStore) {
        List<Embedding> embeddings =
                Stream.generate(TestData::randomEmbedding)
                        .limit(100)
                        .collect(Collectors.toList());

        List<String> ids = embeddingStore.addAll(embeddings);

        return IntStream.range(0, embeddings.size())
                .mapToObj(i -> new TestData(ids.get(i), embeddings.get(i), null))
                .collect(Collectors.toList());
    }

    /**
     * Creates and adds 100 rows of test data with random embeddings, and random text segments.
     *
     * @param embeddingStore Store to add the data to. Not null.
     *
     * @return Test data with a random embeddings, and random text segments.
     */
    static List<TestData> addAllWithTextSegment(EmbeddingStore<TextSegment> embeddingStore) {
        List<Embedding> embeddings =
                Stream.generate(TestData::randomEmbedding)
                        .limit(100)
                        .collect(Collectors.toList());

        List<TextSegment> textSegments =
                Stream.generate(TestData::randomTextSegment)
                        .limit(embeddings.size())
                        .collect(Collectors.toList());

        List<String> ids = embeddingStore.addAll(embeddings, textSegments);

        return IntStream.range(0, embeddings.size())
                .mapToObj(i -> new TestData(ids.get(i), embeddings.get(i), textSegments.get(i)))
                .collect(Collectors.toList());
    }

}
