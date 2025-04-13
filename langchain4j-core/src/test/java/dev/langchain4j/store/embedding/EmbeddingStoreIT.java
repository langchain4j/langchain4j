package dev.langchain4j.store.embedding;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

/**
 * A minimum set of tests that each implementation of {@link EmbeddingStore} must pass.
 */
public abstract class EmbeddingStoreIT extends EmbeddingStoreWithoutMetadataIT {

    protected static final UUID TEST_UUID = UUID.randomUUID();
    protected static final UUID TEST_UUID2 = UUID.randomUUID();

    @Test
    void should_add_embedding_with_segment_with_metadata() {

        Metadata metadata = createMetadata();

        TextSegment segment = TextSegment.from("hello", metadata);
        Embedding embedding = embeddingModel().embed(segment.text()).content();

        String id = embeddingStore().add(embedding, segment);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);

        // then
        assertThat(id).isNotBlank();

        assertThat(searchResult.matches()).hasSize(1);
        EmbeddingMatch<TextSegment> match = searchResult.matches().get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        if (assertEmbedding()) {
            assertThat(match.embedding()).isEqualTo(embedding);
        }

        assertThat(match.embedded().text()).isEqualTo(segment.text());

        assertThat(match.embedded().metadata().getString("string_empty")).isEmpty();
        assertThat(match.embedded().metadata().getString("string_space")).isEqualTo(" ");
        assertThat(match.embedded().metadata().getString("string_abc")).isEqualTo("abc");

        assertThat(match.embedded().metadata().getUUID("uuid")).isEqualTo(TEST_UUID);

        assertThat(match.embedded().metadata().getInteger("integer_min")).isEqualTo(Integer.MIN_VALUE);
        assertThat(match.embedded().metadata().getInteger("integer_minus_1")).isEqualTo(-1);
        assertThat(match.embedded().metadata().getInteger("integer_0")).isEqualTo(0);
        assertThat(match.embedded().metadata().getInteger("integer_1")).isEqualTo(1);
        assertThat(match.embedded().metadata().getInteger("integer_max")).isEqualTo(Integer.MAX_VALUE);

        assertThat(match.embedded().metadata().getLong("long_min")).isEqualTo(Long.MIN_VALUE);
        assertThat(match.embedded().metadata().getLong("long_minus_1")).isEqualTo(-1L);
        assertThat(match.embedded().metadata().getLong("long_0")).isEqualTo(0L);
        assertThat(match.embedded().metadata().getLong("long_1")).isEqualTo(1L);
        if (testLong1746714878034235396()) {
            assertThat(match.embedded().metadata().getLong("long_1746714878034235396")).isEqualTo(1746714878034235396L);
        }
        assertThat(match.embedded().metadata().getLong("long_max")).isEqualTo(Long.MAX_VALUE);

        if (testFloatExactly()) {
            assertThat(match.embedded().metadata().getFloat("float_min")).isEqualTo(-Float.MAX_VALUE);
            assertThat(match.embedded().metadata().getFloat("float_minus_1")).isEqualTo(-1f);
            assertThat(match.embedded().metadata().getFloat("float_0")).isEqualTo(Float.MIN_VALUE);
            assertThat(match.embedded().metadata().getFloat("float_1")).isEqualTo(1f);
            assertThat(match.embedded().metadata().getFloat("float_123")).isEqualTo(1.23456789f);
            assertThat(match.embedded().metadata().getFloat("float_max")).isEqualTo(Float.MAX_VALUE);
        } else {
            double floatPercentage = floatPercentage();
            assertThat(match.embedded().metadata().getFloat("float_min")).isCloseTo(-Float.MAX_VALUE, withPercentage(floatPercentage));
            assertThat(match.embedded().metadata().getFloat("float_minus_1")).isCloseTo(-1f, withPercentage(floatPercentage));
            assertThat(match.embedded().metadata().getFloat("float_0")).isCloseTo(Float.MIN_VALUE, withPercentage(floatPercentage));
            assertThat(match.embedded().metadata().getFloat("float_1")).isCloseTo(1f, withPercentage(floatPercentage));
            assertThat(match.embedded().metadata().getFloat("float_123")).isCloseTo(1.23456789f, withPercentage(floatPercentage));
            assertThat(match.embedded().metadata().getFloat("float_max")).isCloseTo(Float.MAX_VALUE, withPercentage(floatPercentage));
        }

        if (testDoubleExactly()) {
            assertThat(match.embedded().metadata().getDouble("double_minus_1")).isEqualTo(-1d);
            assertThat(match.embedded().metadata().getDouble("double_0")).isEqualTo(Double.MIN_VALUE);
            assertThat(match.embedded().metadata().getDouble("double_1")).isEqualTo(1d);
            assertThat(match.embedded().metadata().getDouble("double_123")).isEqualTo(1.23456789d);
        } else {
            double doublePercentage = doublePercentage();
            assertThat(match.embedded().metadata().getDouble("double_minus_1")).isCloseTo(-1d, withPercentage(doublePercentage));
            assertThat(match.embedded().metadata().getDouble("double_0")).isCloseTo(Double.MIN_VALUE, withPercentage(doublePercentage));
            assertThat(match.embedded().metadata().getDouble("double_1")).isCloseTo(1d, withPercentage(doublePercentage));
            assertThat(match.embedded().metadata().getDouble("double_123")).isCloseTo(1.23456789d, withPercentage(doublePercentage));
        }
    }

    protected boolean testLong1746714878034235396() {
        return true;
    }

    /**
     * Whether to test float metadata exactly or not.
     *
     * @return True if test float metadata exactly.
     */
    protected boolean testFloatExactly() {
        return true;
    }

    /**
     * Whether to test double metadata exactly or not.
     *
     * @return True if test double metadata exactly.
     */
    protected boolean testDoubleExactly() {
        return true;
    }

    /**
     * Float percentage close to.
     *
     * @return float percentage.
     */
    protected double floatPercentage() {
        return 0.01;
    }

    /**
     * Double percentage close to.
     *
     * @return double percentage.
     */
    protected double doublePercentage() {
        return 0.01;
    }

    protected Metadata createMetadata() {

        Metadata metadata = new Metadata();

        metadata.put("string_empty", "");
        metadata.put("string_space", " ");
        metadata.put("string_abc", "abc");

        metadata.put("uuid", TEST_UUID);

        metadata.put("integer_min", Integer.MIN_VALUE);
        metadata.put("integer_minus_1", -1);
        metadata.put("integer_0", 0);
        metadata.put("integer_1", 1);
        metadata.put("integer_max", Integer.MAX_VALUE);

        metadata.put("long_min", Long.MIN_VALUE);
        metadata.put("long_minus_1", -1L);
        metadata.put("long_0", 0L);
        metadata.put("long_1", 1L);
        if (testLong1746714878034235396()) {
            metadata.put("long_1746714878034235396", 1746714878034235396L);
        }
        metadata.put("long_max", Long.MAX_VALUE);

        metadata.put("float_min", -Float.MAX_VALUE);
        metadata.put("float_minus_1", -1f);
        metadata.put("float_0", Float.MIN_VALUE);
        metadata.put("float_1", 1f);
        metadata.put("float_123", 1.23456789f);
        metadata.put("float_max", Float.MAX_VALUE);

        metadata.put("double_minus_1", -1d);
        metadata.put("double_0", Double.MIN_VALUE);
        metadata.put("double_1", 1d);
        metadata.put("double_123", 1.23456789d);

        return metadata;
    }
}
