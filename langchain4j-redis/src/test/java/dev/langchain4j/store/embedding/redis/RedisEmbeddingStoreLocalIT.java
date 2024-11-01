package dev.langchain4j.store.embedding.redis;

import com.redis.testcontainers.RedisContainer;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;

import java.util.*;
import java.util.stream.Stream;

import static com.redis.testcontainers.RedisStackContainer.DEFAULT_IMAGE_NAME;
import static com.redis.testcontainers.RedisStackContainer.DEFAULT_TAG;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.filter.Filter.*;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Map.entry;

class RedisEmbeddingStoreLocalIT extends EmbeddingStoreWithFilteringIT {

    static RedisContainer redis = new RedisContainer(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));

    RedisEmbeddingStore embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void beforeAll() {
        redis.start();
    }

    @AfterAll
    static void afterAll() {
        redis.stop();
    }

    @Override
    protected void clearStore() {
        Map<String, SchemaField> metadataConfig = new HashMap<>();
        Map<String, Object> metadatas = createMetadata().toMap();

        List<Class<? extends Number>> numericPrefix = Arrays.asList(Integer.class, Long.class, Float.class, Double.class);
        Map<String, Class<?>> filterMetadatas = getFilterMetadataConfig();
        filterMetadatas.forEach((key, value) -> {
            if (numericPrefix.stream().anyMatch(type -> type.isAssignableFrom(value))) {
                metadataConfig.put(key, NumericField.of("$." + key).as(key));
            } else if (key.startsWith("UUID")) {
                metadataConfig.put(key, TextField.of("$." + key).as(key).weight(1.0));
            } else {
                metadataConfig.put(key, TagField.of("$." + key).caseSensitive().as(key));
            }
        });
        metadatas.forEach((key, value) -> {
            if (numericPrefix.stream().anyMatch(type -> type.isAssignableFrom(value.getClass()))) {
                metadataConfig.put(key, NumericField.of("$." + key).as(key));
            } else if (key.startsWith("UUID")) {
                metadataConfig.put(key, TextField.of("$." + key).as(key).weight(1.0));
            } else {
                metadataConfig.put(key, TagField.of("$." + key).caseSensitive().as(key));
            }
        });

        embeddingStore = RedisEmbeddingStore.builder()
                .host(redis.getHost())
                .port(redis.getFirstMappedPort())
                .indexName(randomUUID())
                .prefix(randomUUID() + ":")
                .dimension(embeddingModel.dimension())
                .metadataConfig(metadataConfig)
                .build();
    }

    @AfterEach
    void afterEach() {
        embeddingStore.close();
    }

    @Override
    @ParameterizedTest
    @MethodSource("redis_should_filter_by_metadata")
    protected void should_filter_by_metadata(Filter metadataFilter,
                                             List<Metadata> matchingMetadatas,
                                             List<Metadata> notMatchingMetadatas) {
        super.should_filter_by_metadata(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    @Override
    @ParameterizedTest
    @MethodSource("redis_should_filter_by_metadata_not")
    protected void should_filter_by_metadata_not(Filter metadataFilter,
                                                 List<Metadata> matchingMetadatas,
                                                 List<Metadata> notMatchingMetadatas) {
        super.should_filter_by_metadata_not(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    private static Stream<Arguments> redis_should_filter_by_metadata() {
        return Stream.<Arguments>builder()

                // === Equal ===

                .add(Arguments.of(
                        metadataKey("key").isEqualTo("a"),
                        asList(
                                new Metadata().put("key", "a"),
                                new Metadata().put("key", "a").put("key2", "b")
                        ),
                        asList(
                                new Metadata().put("key", "A"),
                                new Metadata().put("key", "b"),
                                new Metadata().put("key", "aa"),
                                new Metadata().put("key", "a a"),
                                new Metadata().put("key2", "a"),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("UUID_key").isEqualTo(TEST_UUID),
                        asList(
                                new Metadata().put("UUID_key", TEST_UUID),
                                new Metadata().put("UUID_key", TEST_UUID).put("key2", "b")
                        ),
                        asList(
                                new Metadata().put("UUID_key", UUID.randomUUID()),
                                new Metadata().put("UUID_key2", TEST_UUID),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("integer_key").isEqualTo(1),
                        asList(
                                new Metadata().put("integer_key", 1),
                                new Metadata().put("integer_key", 1).put("integer_key2", 0)
                        ),
                        asList(
                                new Metadata().put("integer_key", -1),
                                new Metadata().put("integer_key", 0),
                                new Metadata().put("integer_key2", 1),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("long_key").isEqualTo(1L),
                        asList(
                                new Metadata().put("long_key", 1L),
                                new Metadata().put("long_key", 1L).put("long_key2", 0L)
                        ),
                        asList(
                                new Metadata().put("long_key", -1L),
                                new Metadata().put("long_key", 0L),
                                new Metadata().put("long_key2", 1L),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("float_key").isEqualTo(1.23f),
                        asList(
                                new Metadata().put("float_key", 1.23f),
                                new Metadata().put("float_key", 1.23f).put("float_key2", 0f)
                        ),
                        asList(
                                new Metadata().put("float_key", -1.23f),
                                new Metadata().put("float_key", 1.22f),
                                new Metadata().put("float_key", 1.24f),
                                new Metadata().put("float_key2", 1.23f),
                                new Metadata()
                        )
                )).add(Arguments.of(
                        metadataKey("double_key").isEqualTo(1.23d),
                        asList(
                                new Metadata().put("double_key", 1.23d),
                                new Metadata().put("double_key", 1.23d).put("double_key2", 0d)
                        ),
                        asList(
                                new Metadata().put("double_key", -1.23d),
                                new Metadata().put("double_key", 1.22d),
                                new Metadata().put("double_key", 1.24d),
                                new Metadata().put("double_key2", 1.23d),
                                new Metadata()
                        )
                ))


                // === GreaterThan ==

                .add(Arguments.of(
                        metadataKey("integer_key").isGreaterThan(1),
                        asList(
                                new Metadata().put("integer_key", 2),
                                new Metadata().put("integer_key", 2).put("integer_key2", 0)
                        ),
                        asList(
                                new Metadata().put("integer_key", -2),
                                new Metadata().put("integer_key", 0),
                                new Metadata().put("integer_key", 1),
                                new Metadata().put("integer_key2", 2),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("long_key").isGreaterThan(1L),
                        asList(
                                new Metadata().put("long_key", 2L),
                                new Metadata().put("long_key", 2L).put("long_key2", 0L)
                        ),
                        asList(
                                new Metadata().put("long_key", -2L),
                                new Metadata().put("long_key", 0L),
                                new Metadata().put("long_key", 1L),
                                new Metadata().put("long_key2", 2L),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("float_key").isGreaterThan(1.1f),
                        asList(
                                new Metadata().put("float_key", 1.2f),
                                new Metadata().put("float_key", 1.2f).put("float_key2", 1.0f)
                        ),
                        asList(
                                new Metadata().put("float_key", -1.2f),
                                new Metadata().put("float_key", 0.0f),
                                new Metadata().put("float_key", 1.1f),
                                new Metadata().put("float_key2", 1.2f),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("double_key").isGreaterThan(1.1d),
                        asList(
                                new Metadata().put("double_key", 1.2d),
                                new Metadata().put("double_key", 1.2d).put("double_key2", 1.0d)
                        ),
                        asList(
                                new Metadata().put("double_key", -1.2d),
                                new Metadata().put("double_key", 0.0d),
                                new Metadata().put("double_key", 1.1d),
                                new Metadata().put("double_key2", 1.2d),
                                new Metadata()
                        )
                ))


                // === GreaterThanOrEqual ==

                .add(Arguments.of(
                        metadataKey("integer_key").isGreaterThanOrEqualTo(1),
                        asList(
                                new Metadata().put("integer_key", 1),
                                new Metadata().put("integer_key", 2),
                                new Metadata().put("integer_key", 2).put("integer_key2", 0)
                        ),
                        asList(
                                new Metadata().put("integer_key", -2),
                                new Metadata().put("integer_key", -1),
                                new Metadata().put("integer_key", 0),
                                new Metadata().put("integer_key2", 1),
                                new Metadata().put("integer_key2", 2),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("long_key").isGreaterThanOrEqualTo(1L),
                        asList(
                                new Metadata().put("long_key", 1L),
                                new Metadata().put("long_key", 2L),
                                new Metadata().put("long_key", 2L).put("long_key2", 0L)
                        ),
                        asList(
                                new Metadata().put("long_key", -2L),
                                new Metadata().put("long_key", -1L),
                                new Metadata().put("long_key", 0L),
                                new Metadata().put("long_key2", 1L),
                                new Metadata().put("long_key2", 2L),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("float_key").isGreaterThanOrEqualTo(1.1f),
                        asList(
                                new Metadata().put("float_key", 1.1f),
                                new Metadata().put("float_key", 1.2f),
                                new Metadata().put("float_key", 1.2f).put("float_key2", 1.0f)
                        ),
                        asList(
                                new Metadata().put("float_key", -1.2f),
                                new Metadata().put("float_key", -1.1f),
                                new Metadata().put("float_key", 0.0f),
                                new Metadata().put("float_key2", 1.1f),
                                new Metadata().put("float_key2", 1.2f),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("double_key").isGreaterThanOrEqualTo(1.1d),
                        asList(
                                new Metadata().put("double_key", 1.1d),
                                new Metadata().put("double_key", 1.2d),
                                new Metadata().put("double_key", 1.2d).put("double_key2", 1.0d)
                        ),
                        asList(
                                new Metadata().put("double_key", -1.2d),
                                new Metadata().put("double_key", -1.1d),
                                new Metadata().put("double_key", 0.0d),
                                new Metadata().put("double_key2", 1.1d),
                                new Metadata().put("double_key2", 1.2d),
                                new Metadata()
                        )
                ))


                // === LessThan ==

                .add(Arguments.of(
                        metadataKey("integer_key").isLessThan(1),
                        asList(
                                new Metadata().put("integer_key", -2),
                                new Metadata().put("integer_key", 0),
                                new Metadata().put("integer_key", 0).put("integer_key2", 2)
                        ),
                        asList(
                                new Metadata().put("integer_key", 1),
                                new Metadata().put("integer_key", 2),
                                new Metadata().put("integer_key2", 0),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("long_key").isLessThan(1L),
                        asList(
                                new Metadata().put("long_key", -2L),
                                new Metadata().put("long_key", 0L),
                                new Metadata().put("long_key", 0L).put("long_key2", 2L)
                        ),
                        asList(
                                new Metadata().put("long_key", 1L),
                                new Metadata().put("long_key", 2L),
                                new Metadata().put("long_key2", 0L),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("float_key").isLessThan(1.1f),
                        asList(
                                new Metadata().put("float_key", -1.2f),
                                new Metadata().put("float_key", 1.0f),
                                new Metadata().put("float_key", 1.0f).put("float_key2", 1.2f)
                        ),
                        asList(
                                new Metadata().put("float_key", 1.1f),
                                new Metadata().put("float_key", 1.2f),
                                new Metadata().put("float_key2", 1.0f),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("double_key").isLessThan(1.1d),
                        asList(
                                new Metadata().put("double_key", -1.2d),
                                new Metadata().put("double_key", 1.0d),
                                new Metadata().put("double_key", 1.0d).put("double_key2", 1.2d)
                        ),
                        asList(
                                new Metadata().put("double_key", 1.1d),
                                new Metadata().put("double_key", 1.2d),
                                new Metadata().put("double_key2", 1.0d),
                                new Metadata()
                        )
                ))


                // === LessThanOrEqual ==

                .add(Arguments.of(
                        metadataKey("integer_key").isLessThanOrEqualTo(1),
                        asList(
                                new Metadata().put("integer_key", -2),
                                new Metadata().put("integer_key", 0),
                                new Metadata().put("integer_key", 1),
                                new Metadata().put("integer_key", 1).put("integer_key2", 2)
                        ),
                        asList(
                                new Metadata().put("integer_key", 2),
                                new Metadata().put("integer_key2", 0),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("long_key").isLessThanOrEqualTo(1L),
                        asList(
                                new Metadata().put("long_key", -2L),
                                new Metadata().put("long_key", 0L),
                                new Metadata().put("long_key", 1L),
                                new Metadata().put("long_key", 1L).put("long_key2", 2L)
                        ),
                        asList(
                                new Metadata().put("long_key", 2L),
                                new Metadata().put("long_key2", 0L),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("float_key").isLessThanOrEqualTo(1.1f),
                        asList(
                                new Metadata().put("float_key", -1.2f),
                                new Metadata().put("float_key", 1.0f),
                                new Metadata().put("float_key", 1.1f),
                                new Metadata().put("float_key", 1.1f).put("float_key2", 1.2f)
                        ),
                        asList(
                                new Metadata().put("float_key", 1.2f),
                                new Metadata().put("float_key2", 1.0f),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("double_key").isLessThanOrEqualTo(1.1d),
                        asList(
                                new Metadata().put("double_key", -1.2d),
                                new Metadata().put("double_key", 1.0d),
                                new Metadata().put("double_key", 1.1d),
                                new Metadata().put("double_key", 1.1d).put("double_key2", 1.2d)
                        ),
                        asList(
                                new Metadata().put("double_key", 1.2d),
                                new Metadata().put("double_key2", 1.0d),
                                new Metadata()
                        )
                ))


                // === In ===

                // In: string
                .add(Arguments.of(
                        metadataKey("name").isIn("Klaus"),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42)
                        ),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name2", "Klaus"),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("name").isIn(singletonList("Klaus")),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42)
                        ),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name2", "Klaus"),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("name").isIn("Klaus", "Alice"),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name", "Alice").put("integer_age", 42)
                        ),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Zoe"),
                                new Metadata().put("name2", "Klaus"),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("name").isIn(asList("Klaus", "Alice")),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name", "Alice").put("integer_age", 42)
                        ),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Zoe"),
                                new Metadata().put("name2", "Klaus"),
                                new Metadata()
                        )
                ))

                // In: UUID
                .add(Arguments.of(
                        metadataKey("UUID_name").isIn(TEST_UUID),
                        asList(
                                new Metadata().put("UUID_name", TEST_UUID),
                                new Metadata().put("UUID_name", TEST_UUID).put("integer_age", 42)
                        ),
                        asList(
                                new Metadata().put("UUID_name", UUID.randomUUID()),
                                new Metadata().put("UUID_name2", TEST_UUID),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("UUID_name").isIn(singletonList(TEST_UUID)),
                        asList(
                                new Metadata().put("UUID_name", TEST_UUID),
                                new Metadata().put("UUID_name", TEST_UUID).put("integer_age", 42)
                        ),
                        asList(
                                new Metadata().put("UUID_name", UUID.randomUUID()),
                                new Metadata().put("UUID_name2", TEST_UUID),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("UUID_name").isIn(TEST_UUID, TEST_UUID2),
                        asList(
                                new Metadata().put("UUID_name", TEST_UUID),
                                new Metadata().put("UUID_name", TEST_UUID).put("integer_age", 42),
                                new Metadata().put("UUID_name", TEST_UUID2),
                                new Metadata().put("UUID_name", TEST_UUID2).put("integer_age", 42)
                        ),
                        asList(
                                new Metadata().put("UUID_name", UUID.randomUUID()),
                                new Metadata().put("UUID_name2", TEST_UUID),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        metadataKey("UUID_name").isIn(asList(TEST_UUID, TEST_UUID2)),
                        asList(
                                new Metadata().put("UUID_name", TEST_UUID),
                                new Metadata().put("UUID_name", TEST_UUID).put("integer_age", 42),
                                new Metadata().put("UUID_name", TEST_UUID2),
                                new Metadata().put("UUID_name", TEST_UUID2).put("integer_age", 42)
                        ),
                        asList(
                                new Metadata().put("UUID_name", UUID.randomUUID()),
                                new Metadata().put("UUID_name2", TEST_UUID),
                                new Metadata()
                        )
                ))

                // === Or ===

                // Or: one key
                .add(Arguments.of(
                        or(
                                metadataKey("name").isEqualTo("Klaus"),
                                metadataKey("name").isEqualTo("Alice")
                        ),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name", "Alice").put("integer_age", 42)
                        ),
                        asList(
                                new Metadata().put("name", "Zoe"),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        or(
                                metadataKey("name").isEqualTo("Alice"),
                                metadataKey("name").isEqualTo("Klaus")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name", "Alice").put("integer_age", 42),
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42)
                        ),
                        asList(
                                new Metadata().put("name", "Zoe"),
                                new Metadata()
                        )
                ))

                // Or: multiple keys
                .add(Arguments.of(
                        or(
                                metadataKey("name").isEqualTo("Klaus"),
                                metadataKey("integer_age").isEqualTo(42)
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("city", "Munich"),

                                // Or.left is true, Or.right is false
                                new Metadata().put("name", "Klaus").put("integer_age", 666),

                                // only Or.right is present and true
                                new Metadata().put("integer_age", 42),
                                new Metadata().put("integer_age", 42).put("city", "Munich"),

                                // Or.right is true, Or.left is false
                                new Metadata().put("integer_age", 42).put("name", "Alice"),

                                // Or.left and Or.right are both true
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("integer_age", 666),
                                new Metadata().put("name", "Alice").put("integer_age", 666),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        or(
                                metadataKey("integer_age").isEqualTo(42),
                                metadataKey("name").isEqualTo("Klaus")
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().put("integer_age", 42),
                                new Metadata().put("integer_age", 42).put("city", "Munich"),

                                // Or.left is true, Or.right is false
                                new Metadata().put("integer_age", 42).put("name", "Alice"),

                                // only Or.right is present and true
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("city", "Munich"),

                                // Or.right is true, Or.left is false
                                new Metadata().put("name", "Klaus").put("integer_age", 666),

                                // Or.left and Or.right are both true
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("integer_age", 666),
                                new Metadata().put("name", "Alice").put("integer_age", 666),
                                new Metadata()
                        )
                ))

                // Or: x2
                .add(Arguments.of(
                        or(
                                metadataKey("name").isEqualTo("Klaus"),
                                or(
                                        metadataKey("integer_age").isEqualTo(42),
                                        metadataKey("city").isEqualTo("Munich")
                                )
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("country", "Germany"),

                                // Or.left is true, Or.right is false
                                new Metadata().put("name", "Klaus").put("integer_age", 666),
                                new Metadata().put("name", "Klaus").put("city", "Frankfurt"),
                                new Metadata().put("name", "Klaus").put("integer_age", 666).put("city", "Frankfurt"),

                                // only Or.right is present and true
                                new Metadata().put("integer_age", 42),
                                new Metadata().put("integer_age", 42).put("country", "Germany"),
                                new Metadata().put("city", "Munich"),
                                new Metadata().put("city", "Munich").put("country", "Germany"),
                                new Metadata().put("integer_age", 42).put("city", "Munich"),
                                new Metadata().put("integer_age", 42).put("city", "Munich").put("country", "Germany"),

                                // Or.right is true, Or.left is false
                                new Metadata().put("integer_age", 42).put("name", "Alice"),
                                new Metadata().put("city", "Munich").put("name", "Alice"),
                                new Metadata().put("integer_age", 42).put("city", "Munich").put("name", "Alice"),

                                // Or.left and Or.right are both true
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("city", "Munich").put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich").put("country", "Germany")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("integer_age", 666),
                                new Metadata().put("city", "Frankfurt"),
                                new Metadata().put("name", "Alice").put("integer_age", 666),
                                new Metadata().put("name", "Alice").put("city", "Frankfurt"),
                                new Metadata().put("name", "Alice").put("integer_age", 666).put("city", "Frankfurt"),
                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        or(
                                or(
                                        metadataKey("name").isEqualTo("Klaus"),
                                        metadataKey("integer_age").isEqualTo(42)
                                ),
                                metadataKey("city").isEqualTo("Munich")
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("country", "Germany"),
                                new Metadata().put("integer_age", 42),
                                new Metadata().put("integer_age", 42).put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("country", "Germany"),

                                // Or.left is true, Or.right is false
                                new Metadata().put("name", "Klaus").put("city", "Frankfurt"),
                                new Metadata().put("integer_age", 42).put("city", "Frankfurt"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Frankfurt"),

                                // only Or.right is present and true
                                new Metadata().put("city", "Munich"),
                                new Metadata().put("city", "Munich").put("country", "Germany"),

                                // Or.right is true, Or.left is false
                                new Metadata().put("city", "Munich").put("name", "Alice"),
                                new Metadata().put("city", "Munich").put("integer_age", 666),

                                // Or.left and Or.right are both true
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("city", "Munich").put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich").put("country", "Germany")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("integer_age", 666),
                                new Metadata().put("city", "Frankfurt"),
                                new Metadata().put("name", "Alice").put("integer_age", 666),
                                new Metadata().put("name", "Alice").put("city", "Frankfurt"),
                                new Metadata().put("name", "Alice").put("integer_age", 666).put("city", "Frankfurt"),
                                new Metadata()
                        )
                ))

                // === AND ===

                .add(Arguments.of(
                        and(
                                metadataKey("name").isEqualTo("Klaus"),
                                metadataKey("integer_age").isEqualTo(42)
                        ),
                        asList(
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().put("name", "Klaus"),

                                // And.left is true, And.right is false
                                new Metadata().put("name", "Klaus").put("integer_age", 666),

                                // only And.right is present and true
                                new Metadata().put("integer_age", 42),

                                // And.right is true, And.left is false
                                new Metadata().put("integer_age", 42).put("name", "Alice"),

                                // And.left, And.right are both false
                                new Metadata().put("integer_age", 666).put("name", "Alice"),

                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        and(
                                metadataKey("integer_age").isEqualTo(42),
                                metadataKey("name").isEqualTo("Klaus")
                        ),
                        asList(
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().put("integer_age", 42),

                                // And.left is true, And.right is false
                                new Metadata().put("integer_age", 42).put("name", "Alice"),

                                // only And.right is present and true
                                new Metadata().put("name", "Klaus"),

                                // And.right is true, And.left is false
                                new Metadata().put("name", "Klaus").put("integer_age", 666),

                                // And.left, And.right are both false
                                new Metadata().put("integer_age", 666).put("name", "Alice"),

                                new Metadata()
                        )
                ))

                // And: x2
                .add(Arguments.of(
                        and(
                                metadataKey("name").isEqualTo("Klaus"),
                                and(
                                        metadataKey("integer_age").isEqualTo(42),
                                        metadataKey("city").isEqualTo("Munich")
                                )
                        ),
                        asList(
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich").put("country", "Germany")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().put("name", "Klaus"),

                                // And.left is true, And.right is false
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Klaus").put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("integer_age", 666).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Frankfurt"),

                                // only And.right is present and true
                                new Metadata().put("integer_age", 42).put("city", "Munich"),

                                // And.right is true, And.left is false
                                new Metadata().put("integer_age", 42).put("city", "Munich").put("name", "Alice"),

                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        and(
                                and(
                                        metadataKey("name").isEqualTo("Klaus"),
                                        metadataKey("integer_age").isEqualTo(42)
                                ),
                                metadataKey("city").isEqualTo("Munich")
                        ),
                        asList(
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich").put("country", "Germany")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().put("name", "Klaus").put("integer_age", 42),

                                // And.left is true, And.right is false
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Frankfurt"),

                                // only And.right is present and true
                                new Metadata().put("city", "Munich"),

                                // And.right is true, And.left is false
                                new Metadata().put("city", "Munich").put("name", "Klaus"),
                                new Metadata().put("city", "Munich").put("name", "Klaus").put("integer_age", 666),
                                new Metadata().put("city", "Munich").put("integer_age", 42),
                                new Metadata().put("city", "Munich").put("integer_age", 42).put("name", "Alice"),

                                new Metadata()
                        )
                ))

                // === AND + nested OR ===

                .add(Arguments.of(
                        and(
                                metadataKey("name").isEqualTo("Klaus"),
                                or(
                                        metadataKey("integer_age").isEqualTo(42),
                                        metadataKey("city").isEqualTo("Munich")
                                )
                        ),
                        asList(
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("city", "Munich").put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich").put("country", "Germany")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().put("name", "Klaus"),

                                // And.left is true, And.right is false
                                new Metadata().put("name", "Klaus").put("integer_age", 666),
                                new Metadata().put("name", "Klaus").put("city", "Frankfurt"),

                                // only And.right is present and true
                                new Metadata().put("integer_age", 42),
                                new Metadata().put("city", "Munich"),
                                new Metadata().put("integer_age", 42).put("city", "Munich"),

                                // And.right is true, And.left is false
                                new Metadata().put("integer_age", 42).put("name", "Alice"),
                                new Metadata().put("city", "Munich").put("name", "Alice"),
                                new Metadata().put("integer_age", 42).put("city", "Munich").put("name", "Alice"),

                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        and(
                                or(
                                        metadataKey("name").isEqualTo("Klaus"),
                                        metadataKey("integer_age").isEqualTo(42)
                                ),
                                metadataKey("city").isEqualTo("Munich")
                        ),
                        asList(
                                new Metadata().put("name", "Klaus").put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("city", "Munich").put("country", "Germany"),
                                new Metadata().put("integer_age", 42).put("city", "Munich"),
                                new Metadata().put("integer_age", 42).put("city", "Munich").put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich").put("country", "Germany")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("integer_age", 42),
                                new Metadata().put("name", "Klaus").put("integer_age", 42),

                                // And.left is true, And.right is false
                                new Metadata().put("name", "Klaus").put("city", "Frankfurt"),
                                new Metadata().put("integer_age", 42).put("city", "Frankfurt"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Frankfurt"),

                                // only And.right is present and true
                                new Metadata().put("city", "Munich"),

                                // And.right is true, And.left is false
                                new Metadata().put("city", "Munich").put("name", "Alice"),
                                new Metadata().put("city", "Munich").put("integer_age", 666),
                                new Metadata().put("city", "Munich").put("name", "Alice").put("integer_age", 666),

                                new Metadata()
                        )
                ))

                // === OR + nested AND ===
                .add(Arguments.of(
                        or(
                                metadataKey("name").isEqualTo("Klaus"),
                                and(
                                        metadataKey("integer_age").isEqualTo(42),
                                        metadataKey("city").isEqualTo("Munich")
                                )
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("country", "Germany"),

                                // Or.left is true, Or.right is false
                                new Metadata().put("name", "Klaus").put("integer_age", 666),
                                new Metadata().put("name", "Klaus").put("city", "Frankfurt"),
                                new Metadata().put("name", "Klaus").put("integer_age", 666).put("city", "Frankfurt"),

                                // only Or.right is present and true
                                new Metadata().put("integer_age", 42).put("city", "Munich"),
                                new Metadata().put("integer_age", 42).put("city", "Munich").put("country", "Germany"),

                                // Or.right is true, Or.left is false
                                new Metadata().put("integer_age", 42).put("city", "Munich").put("name", "Alice")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("integer_age", 666),
                                new Metadata().put("city", "Frankfurt"),
                                new Metadata().put("name", "Alice").put("integer_age", 666).put("city", "Frankfurt"),

                                new Metadata()
                        )
                ))
                .add(Arguments.of(
                        or(
                                and(
                                        metadataKey("name").isEqualTo("Klaus"),
                                        metadataKey("integer_age").isEqualTo(42)
                                ),
                                metadataKey("city").isEqualTo("Munich")
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("country", "Germany"),

                                // Or.left is true, Or.right is false
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Frankfurt"),

                                // only Or.right is present and true
                                new Metadata().put("city", "Munich"),
                                new Metadata().put("city", "Munich").put("country", "Germany"),

                                // Or.right is true, Or.left is true
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42).put("city", "Munich").put("country", "Germany")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("integer_age", 666),
                                new Metadata().put("city", "Frankfurt"),
                                new Metadata().put("name", "Alice").put("integer_age", 666).put("city", "Frankfurt"),
                                new Metadata()
                        )
                ))

                .build();
    }

    private static Stream<Arguments> redis_should_filter_by_metadata_not() {
        return Stream.<Arguments>builder()

                // === Not ===
                .add(Arguments.of(
                        not(
                                metadataKey("name").isEqualTo("Klaus")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("integer_age", 42),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42)
                        )
                ))


                // === NotEqual ===

                .add(Arguments.of(
                        metadataKey("key").isNotEqualTo("a"),
                        asList(
                                new Metadata().put("key", "A"),
                                new Metadata().put("key", "b"),
                                new Metadata().put("key", "aa"),
                                new Metadata().put("key", "a a"),
                                new Metadata().put("key2", "a"),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("key", "a"),
                                new Metadata().put("key", "a").put("key2", "b")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("UUID_key").isNotEqualTo(TEST_UUID),
                        asList(
                                new Metadata().put("UUID_key", UUID.randomUUID()),
                                new Metadata().put("UUID_key2", TEST_UUID),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("UUID_key", TEST_UUID),
                                new Metadata().put("UUID_key", TEST_UUID).put("UUID_key2", UUID.randomUUID())
                        )
                ))
                .add(Arguments.of(
                        metadataKey("integer_key").isNotEqualTo(1),
                        asList(
                                new Metadata().put("integer_key", -1),
                                new Metadata().put("integer_key", 0),
                                new Metadata().put("integer_key", 2),
                                new Metadata().put("integer_key", 10),
                                new Metadata().put("integer_key2", 1),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("integer_key", 1),
                                new Metadata().put("integer_key", 1).put("integer_key2", 2)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("long_key").isNotEqualTo(1L),
                        asList(
                                new Metadata().put("long_key", -1L),
                                new Metadata().put("long_key", 0L),
                                new Metadata().put("long_key", 2L),
                                new Metadata().put("long_key", 10L),
                                new Metadata().put("long_key2", 1L),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("long_key", 1L),
                                new Metadata().put("long_key", 1L).put("long_key2", 2L)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("float_key").isNotEqualTo(1.1f),
                        asList(
                                new Metadata().put("float_key", -1.1f),
                                new Metadata().put("float_key", 0.0f),
                                new Metadata().put("float_key", 1.11f),
                                new Metadata().put("float_key", 2.2f),
                                new Metadata().put("float_key2", 1.1f),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("float_key", 1.1f),
                                new Metadata().put("float_key", 1.1f).put("float_key2", 2.2f)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("double_key").isNotEqualTo(1.1),
                        asList(
                                new Metadata().put("double_key", -1.1),
                                new Metadata().put("double_key", 0.0),
                                new Metadata().put("double_key", 1.11),
                                new Metadata().put("double_key", 2.2),
                                new Metadata().put("double_key2", 1.1),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("double_key", 1.1),
                                new Metadata().put("double_key", 1.1).put("double_key2", 2.2)
                        )
                ))


                // === NotIn ===

                // NotIn: string
                .add(Arguments.of(
                        metadataKey("name").isNotIn("Klaus"),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name2", "Klaus"),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("name").isNotIn(singletonList("Klaus")),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name2", "Klaus"),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("name").isNotIn("Klaus", "Alice"),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Zoe"),
                                new Metadata().put("name2", "Klaus"),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name", "Alice").put("integer_age", 42)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("name").isNotIn(asList("Klaus", "Alice")),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Zoe"),
                                new Metadata().put("name2", "Klaus"),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("integer_age", 42),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name", "Alice").put("integer_age", 42)
                        )
                ))

                // NotIn: UUID
                .add(Arguments.of(
                        metadataKey("UUID_name").isNotIn(TEST_UUID),
                        asList(
                                new Metadata().put("UUID_name", UUID.randomUUID()),
                                new Metadata().put("UUID_name2", TEST_UUID),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("UUID_name", TEST_UUID),
                                new Metadata().put("UUID_name", TEST_UUID).put("integer_age", 42)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("UUID_name").isNotIn(singletonList(TEST_UUID)),
                        asList(
                                new Metadata().put("UUID_name", UUID.randomUUID()),
                                new Metadata().put("UUID_name", TEST_UUID2),
                                new Metadata().put("UUID_name2", TEST_UUID),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("UUID_name", TEST_UUID),
                                new Metadata().put("UUID_name", TEST_UUID).put("integer_age", 42)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("UUID_name").isNotIn(TEST_UUID, TEST_UUID2),
                        asList(
                                new Metadata().put("UUID_name", UUID.randomUUID()),
                                new Metadata().put("UUID_name2", TEST_UUID),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("UUID_name", TEST_UUID),
                                new Metadata().put("UUID_name", TEST_UUID).put("integer_age", 42),
                                new Metadata().put("UUID_name", TEST_UUID2),
                                new Metadata().put("UUID_name", TEST_UUID2).put("integer_age", 42)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("UUID_name").isNotIn(asList(TEST_UUID, TEST_UUID2)),
                        asList(
                                new Metadata().put("UUID_name", UUID.randomUUID()),
                                new Metadata().put("UUID_name2", TEST_UUID),
                                new Metadata()
                        ),
                        asList(
                                new Metadata().put("UUID_name", TEST_UUID),
                                new Metadata().put("UUID_name", TEST_UUID).put("integer_age", 42),
                                new Metadata().put("UUID_name", TEST_UUID2),
                                new Metadata().put("UUID_name", TEST_UUID2).put("integer_age", 42)
                        )
                ))
                .build();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    protected static Map<String, Class<?>> getFilterMetadataConfig() {
        // To create metadata config
        return Map.ofEntries(
                entry("integer_key", Integer.class),
                entry("integer_key2", Integer.class),
                entry("long_key", Long.class),
                entry("long_key2", Long.class),
                entry("float_key", Float.class),
                entry("float_key2", Float.class),
                entry("double_key", Double.class),
                entry("double_key2", Double.class),
                entry("integer_age", Integer.class),
                entry("integer_age2", Integer.class),
                entry("UUID_key", UUID.class),
                entry("UUID_Key2", UUID.class),

                entry("UUID_name", UUID.class),
                entry("UUID_name2", UUID.class),
                entry("key", String.class),
                entry("key2", String.class),
                entry("city", String.class),
                entry("name", String.class),
                entry("country", String.class)
        );
    }
}
