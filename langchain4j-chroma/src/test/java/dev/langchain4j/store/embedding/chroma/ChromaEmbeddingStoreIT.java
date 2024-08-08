package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.Ignore;
import org.junit.jupiter.params.provider.Arguments;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ChromaEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    @Container
    private static final ChromaDBContainer chroma = new ChromaDBContainer("chromadb/chroma:0.5.4");

    EmbeddingStore<TextSegment> embeddingStore = ChromaEmbeddingStore
        .builder()
        .baseUrl(chroma.getEndpoint())
        .collectionName(randomUUID())
        .logRequests(true)
        .logResponses(true)
        .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    @Ignore("Chroma cannot filter by greater and less than of alphanumeric metadata, only int and float are supported")
    protected void should_filter_by_greater_and_less_than_alphanumeric_metadata(
        Filter metadataFilter,
        List<Metadata> matchingMetadatas,
        List<Metadata> notMatchingMetadatas
    ) {}

    // Chroma filters by *not* as following:
    // If you filter by "key" not equals "a", then in fact all items with "key" != "a" value are returned, but no items
    // without "key" metadata!
    // Therefore, all default *not* tests coming from parent class have to be rewritten here.
    protected static Stream<Arguments> should_filter_by_metadata_not() {
        return Stream
            .<Arguments>builder()
            // === NotEqual ===

            .add(
                Arguments.of(
                    metadataKey("key").isNotEqualTo("a"),
                    asList(
                        new Metadata().put("key", "A"),
                        new Metadata().put("key", "b"),
                        new Metadata().put("key", "aa"),
                        new Metadata().put("key", "a a")
                    ),
                    asList(
                        new Metadata().put("key", "a"),
                        new Metadata().put("key2", "a"),
                        new Metadata().put("key", "a").put("key2", "b")
                    ),
                    false
                )
            )
            .add(
                Arguments.of(
                    metadataKey("key").isNotEqualTo(TEST_UUID),
                    asList(new Metadata().put("key", UUID.randomUUID())),
                    asList(
                        new Metadata().put("key", TEST_UUID),
                        new Metadata().put("key2", TEST_UUID),
                        new Metadata().put("key", TEST_UUID).put("key2", UUID.randomUUID())
                    ),
                    false
                )
            )
            .add(
                Arguments.of(
                    metadataKey("key").isNotEqualTo(1),
                    asList(
                        new Metadata().put("key", -1),
                        new Metadata().put("key", 0),
                        new Metadata().put("key", 2),
                        new Metadata().put("key", 10)
                    ),
                    asList(
                        new Metadata().put("key", 1),
                        new Metadata().put("key2", 1),
                        new Metadata().put("key", 1).put("key2", 2)
                    ),
                    false
                )
            )
            .add(
                Arguments.of(
                    metadataKey("key").isNotEqualTo(1.1f),
                    asList(
                        new Metadata().put("key", -1.1f),
                        new Metadata().put("key", 0.0f),
                        new Metadata().put("key", 1.11f),
                        new Metadata().put("key", 2.2f)
                    ),
                    asList(
                        new Metadata().put("key", 1.1f),
                        new Metadata().put("key2", 1.1f),
                        new Metadata().put("key", 1.1f).put("key2", 2.2f)
                    ),
                    false
                )
            )
            // === NotIn ===

            // NotIn: string
            .add(
                Arguments.of(
                    metadataKey("name").isNotIn("Klaus"),
                    asList(new Metadata().put("name", "Klaus Heisler"), new Metadata().put("name", "Alice")),
                    asList(
                        new Metadata().put("name", "Klaus"),
                        new Metadata().put("name2", "Klaus"),
                        new Metadata().put("name", "Klaus").put("age", 42)
                    ),
                    false
                )
            )
            .add(
                Arguments.of(
                    metadataKey("name").isNotIn(singletonList("Klaus")),
                    asList(new Metadata().put("name", "Klaus Heisler"), new Metadata().put("name", "Alice")),
                    asList(
                        new Metadata().put("name", "Klaus"),
                        new Metadata().put("name2", "Klaus"),
                        new Metadata().put("name", "Klaus").put("age", 42)
                    ),
                    false
                )
            )
            .add(
                Arguments.of(
                    metadataKey("name").isNotIn("Klaus", "Alice"),
                    asList(new Metadata().put("name", "Klaus Heisler"), new Metadata().put("name", "Zoe")),
                    asList(
                        new Metadata().put("name", "Klaus"),
                        new Metadata().put("name2", "Klaus"),
                        new Metadata().put("name", "Klaus").put("age", 42),
                        new Metadata().put("name", "Alice"),
                        new Metadata().put("name", "Alice").put("age", 42)
                    ),
                    false
                )
            )
            // NotIn: UUID
            .add(
                Arguments.of(
                    metadataKey("name").isNotIn(TEST_UUID),
                    asList(new Metadata().put("name", UUID.randomUUID())),
                    asList(
                        new Metadata().put("name", TEST_UUID),
                        new Metadata().put("name2", TEST_UUID),
                        new Metadata().put("name", TEST_UUID).put("age", 42)
                    ),
                    false
                )
            )
            // NotIn: int
            .add(
                Arguments.of(
                    metadataKey("age").isNotIn(42),
                    asList(new Metadata().put("age", 666)),
                    asList(
                        new Metadata().put("age", 42),
                        new Metadata().put("age2", 42),
                        new Metadata().put("age", 42).put("name", "Klaus")
                    ),
                    false
                )
            )
            .add(
                Arguments.of(
                    metadataKey("age").isNotIn(42, 18),
                    asList(new Metadata().put("age", 666)),
                    asList(
                        new Metadata().put("age", 42),
                        new Metadata().put("age", 18),
                        new Metadata().put("age2", 42),
                        new Metadata().put("age", 42).put("name", "Klaus"),
                        new Metadata().put("age", 18).put("name", "Klaus")
                    ),
                    false
                )
            )
            // NotIn: float
            .add(
                Arguments.of(
                    metadataKey("age").isNotIn(asList(42.0f, 18.0f)),
                    asList(new Metadata().put("age", 666.0f)),
                    asList(
                        new Metadata().put("age", 42.0f),
                        new Metadata().put("age", 18.0f),
                        new Metadata().put("age2", 42.0f),
                        new Metadata().put("age", 42.0f).put("name", "Klaus"),
                        new Metadata().put("age", 18.0f).put("name", "Klaus")
                    ),
                    false
                )
            )
            .build();
    }
}
