package dev.langchain4j.store.embedding;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.filter.MetadataFilter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.store.embedding.filter.MetadataFilter.MetadataKey.key;
import static dev.langchain4j.store.embedding.filter.MetadataFilter.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

/**
 * A minimum set of tests that each implementation of {@link EmbeddingStore} that supports metadata filtering must pass.
 */
public abstract class EmbeddingStoreWithMetadataFilteringIT extends EmbeddingStoreIT {

    @ParameterizedTest
    @MethodSource
    void should_filter_by_metadata(MetadataFilter metadataFilter,
                                   List<Metadata> matchingMetadatas,
                                   List<Metadata> notMatchingMetadatas) {

        // given
        for (Metadata matchingMetadata : matchingMetadatas) {
            TextSegment matchingSegment = TextSegment.from("matching", matchingMetadata);
            Embedding matchingEmbedding = embeddingModel().embed(matchingSegment).content();
            embeddingStore().add(matchingEmbedding, matchingSegment);
        }

        for (Metadata notMatchingMetadata : notMatchingMetadatas) {
            TextSegment notMatchingSegment = TextSegment.from("not matching", notMatchingMetadata);
            Embedding notMatchingEmbedding = embeddingModel().embed(notMatchingSegment).content();
            embeddingStore().add(notMatchingEmbedding, notMatchingSegment);
        }

        TextSegment notMatchingSegmentWithoutMetadata = TextSegment.from("not matching, without metadata");
        Embedding notMatchingWithoutMetadataEmbedding = embeddingModel().embed(notMatchingSegmentWithoutMetadata).content();
        embeddingStore().add(notMatchingWithoutMetadataEmbedding, notMatchingSegmentWithoutMetadata);

        awaitUntilPersisted();

        Embedding queryEmbedding = embeddingModel().embed("matching").content();

        assertThat(embeddingStore().search(SearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(100)
                .build()))
                .hasSize(matchingMetadatas.size() + notMatchingMetadatas.size() + 1);

        SearchRequest searchRequest = SearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .metadataFilter(metadataFilter)
                .maxResults(100)
                .build();

        // when
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore().search(searchRequest);

        // then
        assertThat(matches).hasSize(matchingMetadatas.size());
        assertThat(matches.stream().map(match -> match.embedded().metadata()).collect(toSet()))
                .isEqualTo(new HashSet<>(matchingMetadatas));
        matches.forEach(match -> assertThat(match.embedded().text()).isEqualTo("matching"));
        matches.forEach(match -> assertThat(match.score()).isCloseTo(1, withPercentage(0.01)));
        // TODO fix int/long float/double types assertThat(matches.get(0).embedded()).isEqualTo(matchingSegment);
    }

    static Stream<Arguments> should_filter_by_metadata() {
        return Stream.<Arguments>builder()


                // === Equal ===

                // Equal: strings
                .add(Arguments.of(
                        key("key").eq("a"),
                        asList(
                                new Metadata().add("key", "a"),
                                new Metadata().add("key", "a").add("key2", "b")
                        ),
                        asList(
                                new Metadata().add("key", "A"),
                                new Metadata().add("key", "b"),
                                new Metadata().add("key", "aa"),
                                new Metadata().add("key", "a a"),
                                new Metadata().add("key2", "a")
                        )
                ))

//                 Equal: primitives

//                .add(Arguments.of(
//                        key("key").eq((byte) 1),
//                        asList(
//                                new Metadata().add("key", (byte) 1),
//                                new Metadata().add("key", (byte) 1).add("key2", (byte) 2)
//                        ),
//                        asList(
//                                new Metadata().add("key", (byte) -1),
//                                new Metadata().add("key", (byte) 0),
//                                new Metadata().add("key", (byte) 2),
//                                new Metadata().add("key", (byte) 10),
//                                new Metadata().add("key2", (byte) 1)
//                        )
//                ))
//                .add(Arguments.of(
//                        key("key").eq((short) 1),
//                        asList(
//                                new Metadata().add("key", (short) 1),
//                                new Metadata().add("key", (short) 1).add("key2", (short) 2)
//                        ),
//                        asList(
//                                new Metadata().add("key", (short) -1),
//                                new Metadata().add("key", (short) 0),
//                                new Metadata().add("key", (short) 2),
//                                new Metadata().add("key", (short) 10),
//                                new Metadata().add("key2", (short) 1)
//                        )
//                ))
                .add(Arguments.of(
                        key("key").eq(1),
                        asList(
                                new Metadata().add("key", 1),
                                new Metadata().add("key", 1).add("key2", 2)
                        ),
                        asList(
                                new Metadata().add("key", -1),
                                new Metadata().add("key", 0),
                                new Metadata().add("key", 2),
                                new Metadata().add("key", 10),
                                new Metadata().add("key2", 1)
                        )
                ))
                .add(Arguments.of(
                        key("key").eq(1L),
                        asList(
                                new Metadata().add("key", 1L),
                                new Metadata().add("key", 1L).add("key2", 2L)
                        ),
                        asList(
                                new Metadata().add("key", -1L),
                                new Metadata().add("key", 0L),
                                new Metadata().add("key", 2L),
                                new Metadata().add("key", 10L),
                                new Metadata().add("key2", 1L)
                        )
                ))
//                .add(Arguments.of(
//                        key("key").eq(1.1f),
//                        asList(
//                                new Metadata().add("key", 1.1f),
//                                new Metadata().add("key", 1.1f).add("key2", 2.2f)
//                        ),
//                        asList(
//                                new Metadata().add("key", -1.1f),
//                                new Metadata().add("key", 0.0f),
//                                new Metadata().add("key", 1.11f),
//                                new Metadata().add("key", 2.2f),
//                                new Metadata().add("key2", 1.1f)
//                        )
//                ))
                .add(Arguments.of(
                        key("key").eq(1.1),
                        asList(
                                new Metadata().add("key", 1.1),
                                new Metadata().add("key", 1.1).add("key2", 2.2)
                        ),
                        asList(
                                new Metadata().add("key", -1.1),
                                new Metadata().add("key", 0.0),
                                new Metadata().add("key", 1.11),
                                new Metadata().add("key", 2.2),
                                new Metadata().add("key2", 1.1)
                        )
                ))
                .add(Arguments.of(
                        key("key").eq(true),
                        asList(
                                new Metadata().add("key", true),
                                new Metadata().add("key", true).add("key2", false)
                        ),
                        asList(
                                new Metadata().add("key", false),
                                new Metadata().add("key2", true)
                        )
                ))
//                .add(Arguments.of(
//                        key("key").eq('a'),
//                        asList(
//                                new Metadata().add("key", 'a'),
//                                new Metadata().add("key", 'a').add("key2", 'b')
//                        ),
//                        asList(
//                                new Metadata().add("key", 'b'),
//                                new Metadata().add("key2", 'a')
//
//                        )
//                ))

                // Equal: wrappers
//                .add(Arguments.of(
//                        key("key").eq(Byte.valueOf("1")),
//                        asList(
//                                new Metadata().add("key", Byte.valueOf("1")),
//                                new Metadata().add("key", Byte.valueOf("1")).add("key2", Byte.valueOf("2"))
//                        ),
//                        asList(
//                                new Metadata().add("key", Byte.valueOf("-1")),
//                                new Metadata().add("key", Byte.valueOf("0")),
//                                new Metadata().add("key", Byte.valueOf("2")),
//                                new Metadata().add("key", Byte.valueOf("10")),
//                                new Metadata().add("key2", Byte.valueOf("1"))
//                        )
//                ))
//                .add(Arguments.of(
//                        key("key").eq(Short.valueOf("1")),
//                        asList(
//                                new Metadata().add("key", Short.valueOf("1")),
//                                new Metadata().add("key", Short.valueOf("1")).add("key2", Short.valueOf("2"))
//                        ),
//                        asList(
//                                new Metadata().add("key", Short.valueOf("-1")),
//                                new Metadata().add("key", Short.valueOf("0")),
//                                new Metadata().add("key", Short.valueOf("2")),
//                                new Metadata().add("key", Short.valueOf("10")),
//                                new Metadata().add("key2", Short.valueOf("1"))
//                        )
//                ))
                .add(Arguments.of(
                        key("key").eq(Integer.valueOf("1")),
                        asList(
                                new Metadata().add("key", Integer.valueOf("1")),
                                new Metadata().add("key", Integer.valueOf("1")).add("key2", Integer.valueOf("2"))
                        ),
                        asList(
                                new Metadata().add("key", Integer.valueOf("-1")),
                                new Metadata().add("key", Integer.valueOf("0")),
                                new Metadata().add("key", Integer.valueOf("2")),
                                new Metadata().add("key", Integer.valueOf("10")),
                                new Metadata().add("key2", Integer.valueOf("1"))
                        )
                ))
                .add(Arguments.of(
                        key("key").eq(Long.valueOf("1")),
                        asList(
                                new Metadata().add("key", Long.valueOf("1")),
                                new Metadata().add("key", Long.valueOf("1")).add("key2", Long.valueOf("2"))
                        ),
                        asList(
                                new Metadata().add("key", Long.valueOf("-1")),
                                new Metadata().add("key", Long.valueOf("0")),
                                new Metadata().add("key", Long.valueOf("2")),
                                new Metadata().add("key", Long.valueOf("10")),
                                new Metadata().add("key2", Long.valueOf("1"))
                        )
                ))
//                .add(Arguments.of(
//                        key("key").eq(Float.valueOf("1.1")),
//                        asList(
//                                new Metadata().add("key", Float.valueOf("1.1")),
//                                new Metadata().add("key", Float.valueOf("1.1")).add("key2", Float.valueOf("2.2"))
//                        ),
//                        asList(
//                                new Metadata().add("key", Float.valueOf("-1.1")),
//                                new Metadata().add("key", Float.valueOf("0.0")),
//                                new Metadata().add("key", Float.valueOf("1.11")),
//                                new Metadata().add("key", Float.valueOf("2.2")),
//                                new Metadata().add("key2", Float.valueOf("1.1"))
//                        )
//                ))
                .add(Arguments.of(
                        key("key").eq(Double.valueOf("1.1")),
                        asList(
                                new Metadata().add("key", Double.valueOf("1.1")),
                                new Metadata().add("key", Double.valueOf("1.1")).add("key2", Double.valueOf("2.2"))
                        ),
                        asList(
                                new Metadata().add("key", Double.valueOf("-1.1")),
                                new Metadata().add("key", Double.valueOf("0.0")),
                                new Metadata().add("key", Double.valueOf("1.11")),
                                new Metadata().add("key", Double.valueOf("2.2")),
                                new Metadata().add("key2", Double.valueOf("1.1"))
                        )
                ))
                .add(Arguments.of(
                        key("key").eq(Boolean.TRUE),
                        asList(
                                new Metadata().add("key", Boolean.TRUE),
                                new Metadata().add("key", Boolean.TRUE).add("key2", Boolean.FALSE)
                        ),
                        asList(
                                new Metadata().add("key", Boolean.FALSE),
                                new Metadata().add("key2", Boolean.TRUE)
                        )
                ))
//                .add(Arguments.of(
//                        key("key").eq(Character.valueOf('a')),
//                        asList(
//                                new Metadata().add("key", Character.valueOf('a')),
//                                new Metadata().add("key", Character.valueOf('a')).add("key2", Character.valueOf('b'))
//                        ),
//                        asList(
//                                new Metadata().add("key", Character.valueOf('b')),
//                                new Metadata().add("key2", Character.valueOf('a'))
//                        )
//                ))


                // === GreaterThan ==

                // GreaterThan: strings
                .add(Arguments.of(
                        key("key").gt("a"),
                        asList(
                                new Metadata().add("key", "b"),
                                new Metadata().add("key", "b").add("key2", "c")
                        ),
                        singletonList(new Metadata().add("key", "a"))
                ))

                // GreaterThan: primitives
//                .add(Arguments.of(
//                        key("key").gt((byte) 1),
//                        asList(
//                                new Metadata().add("key", (byte) 2),
//                                new Metadata().add("key", (byte) 2).add("key2", (byte) 1)
//                        ),
//                        singletonList(new Metadata().add("key", (byte) 1))
//                ))
//                .add(Arguments.of(
//                        key("key").gt((short) 1),
//                        asList(
//                                new Metadata().add("key", (short) 2),
//                                new Metadata().add("key", (short) 2).add("key2", (short) 1)
//                        ),
//                        singletonList(new Metadata().add("key", (short) 1))
//                ))
                .add(Arguments.of(
                        key("key").gt(1),
                        asList(
                                new Metadata().add("key", 2),
                                new Metadata().add("key", 2).add("key2", 1)
                        ),
                        singletonList(new Metadata().add("key", 1))
                ))
//                .add(Arguments.of(
//                        key("key").gt(1L),
//                        asList(
//                                new Metadata().add("key", 2L),
//                                new Metadata().add("key", 2L).add("key2", 1L)
//                        ),
//                        singletonList(new Metadata().add("key", 1L))
//                ))
//                .add(Arguments.of(
//                        key("key").gt(1.1f),
//                        asList(
//                                new Metadata().add("key", 1.2f),
//                                new Metadata().add("key", 1.2f).add("key2", 1.1f)
//                        ),
//                        singletonList(new Metadata().add("key", 1.1f))
//                ))
                .add(Arguments.of(
                        key("key").gt(1.1),
                        asList(
                                new Metadata().add("key", 1.2),
                                new Metadata().add("key", 1.2).add("key2", 1.1)
                        ),
                        singletonList(new Metadata().add("key", 1.1))
                ))
//                .add(Arguments.of(key("key").gt(true), new Metadata().add("key", true), new Metadata().add("key", false)))
//                .add(Arguments.of(
//                        key("key").gt('a'),
//                        asList(
//                                new Metadata().add("key", 'b'),
//                                new Metadata().add("key", 'b').add("key2", 'a')
//                        ),
//                        singletonList(new Metadata().add("key", 'a'))
//                ))

                // GreaterThan: wrappers
//                .add(Arguments.of(
//                        key("key").gt(Byte.valueOf("1")),
//                        asList(
//                                new Metadata().add("key", Byte.valueOf("2")),
//                                new Metadata().add("key", Byte.valueOf("2")).add("key2", Byte.valueOf("1"))
//                        ),
//                        singletonList(new Metadata().add("key", Byte.valueOf("1")))
//                ))
//                .add(Arguments.of(
//                        key("key").gt(Short.valueOf("1")),
//                        asList(
//                                new Metadata().add("key", Short.valueOf("2")),
//                                new Metadata().add("key", Short.valueOf("2")).add("key2", Short.valueOf("1"))
//                        ),
//                        singletonList(new Metadata().add("key", Short.valueOf("1")))
//                ))
                .add(Arguments.of(
                        key("key").gt(Integer.valueOf("1")),
                        asList(
                                new Metadata().add("key", Integer.valueOf("2")),
                                new Metadata().add("key", Integer.valueOf("2")).add("key2", Integer.valueOf("1"))
                        ),
                        singletonList(new Metadata().add("key", Integer.valueOf("1")))
                ))
//                .add(Arguments.of(
//                        key("key").gt(Long.valueOf("1")),
//                        asList(
//                                new Metadata().add("key", Long.valueOf("2")),
//                                new Metadata().add("key", Long.valueOf("2")).add("key2", Long.valueOf("1"))
//                        ),
//                        singletonList(new Metadata().add("key", Long.valueOf("1")))
//                ))
//                .add(Arguments.of(
//                        key("key").gt(Float.valueOf("1.1")),
//                        asList(
//                                new Metadata().add("key", Float.valueOf("1.2")),
//                                new Metadata().add("key", Float.valueOf("1.2")).add("key2", Float.valueOf("1.1"))
//                        ),
//                        singletonList(new Metadata().add("key", Float.valueOf("1.1")))
//                ))
                .add(Arguments.of(
                        key("key").gt(Double.valueOf("1.1")),
                        asList(
                                new Metadata().add("key", Double.valueOf("1.2")),
                                new Metadata().add("key", Double.valueOf("1.2")).add("key2", Double.valueOf("1.1"))
                        ),
                        singletonList(new Metadata().add("key", Double.valueOf("1.1")))
                ))
//                .add(Arguments.of(key("key").gt(true), new Metadata().add("key", true), new Metadata().add("key", false)))
//                .add(Arguments.of(
//                        key("key").gt(Character.valueOf('a')),
//                        asList(
//                                new Metadata().add("key", Character.valueOf('b')),
//                                new Metadata().add("key", Character.valueOf('b')).add("key2", Character.valueOf('a'))
//                        ),
//                        singletonList(new Metadata().add("key", Character.valueOf('a')))
//                ))


                // === GreaterThanOrEqual ==
                // TODO


                // === In ===

                // In: strings
                .add(Arguments.of(
                        key("name").in("Klaus"),
                        asList(
                                new Metadata().add("name", "Klaus"),
                                new Metadata().add("name", "Klaus").add("age", 42)
                        ),
                        asList(
                                new Metadata().add("name", "Klaus Heissler"),
                                new Metadata().add("name", "Alice")
                        )
                ))
                .add(Arguments.of(
                        key("name").in("Klaus", "Alice"),
                        asList(
                                new Metadata().add("name", "Klaus"),
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Alice"),
                                new Metadata().add("name", "Alice").add("age", 42)
                        ),
                        asList(
                                new Metadata().add("name", "Klaus Heissler"),
                                new Metadata().add("name", "Zoe")
                        )
                ))

                // TODO In: other types?

                // In: integers
                .add(Arguments.of(
                        key("age").in(42),
                        asList(
                                new Metadata().add("age", 42),
                                new Metadata().add("age", 42).add("name", "Klaus")
                        ),
                        singletonList(
                                new Metadata().add("age", 666)
                        )
                ))
                .add(Arguments.of(
                        key("age").in(new HashSet<>(singletonList(42))),
                        asList(
                                new Metadata().add("age", 42),
                                new Metadata().add("age", 42).add("name", "Klaus")
                        ),
                        singletonList(
                                new Metadata().add("age", 666)
                        )
                ))
                .add(Arguments.of(
                        key("age").in(42, 18),
                        asList(
                                new Metadata().add("age", 42),
                                new Metadata().add("age", 18),
                                new Metadata().add("age", 42).add("name", "Klaus"),
                                new Metadata().add("age", 18).add("name", "Klaus")
                        ),
                        singletonList(
                                new Metadata().add("age", 666)
                        )
                ))
                .add(Arguments.of(
                        key("age").in(new HashSet<>(asList(42, 18))),
                        asList(
                                new Metadata().add("age", 42),
                                new Metadata().add("age", 18),
                                new Metadata().add("age", 42).add("name", "Klaus"),
                                new Metadata().add("age", 18).add("name", "Klaus")
                        ),
                        singletonList(
                                new Metadata().add("age", 666)
                        )
                ))

                // TODO In: array in metadata?


                // === LessThan ===
                // TODO


                // === LessThanOrEqual ===
                // TODO


                // === NotEqual ===
                // TODO


                // === NotIn ===
                // TODO


                // === Or ===

                // Or: one key
                .add(Arguments.of(
                        or(
                                key("name").eq("Klaus"),
                                key("name").eq("Alice")
                        ),
                        asList(
                                new Metadata().add("name", "Klaus"),
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Alice"),
                                new Metadata().add("name", "Alice").add("age", 42)
                        ),
                        singletonList(
                                new Metadata().add("name", "Zoe")
                        )
                ))
                .add(Arguments.of(
                        or(
                                key("name").eq("Alice"),
                                key("name").eq("Klaus")
                        ),
                        asList(
                                new Metadata().add("name", "Alice"),
                                new Metadata().add("name", "Alice").add("age", 42),
                                new Metadata().add("name", "Klaus"),
                                new Metadata().add("name", "Klaus").add("age", 42)
                        ),
                        singletonList(
                                new Metadata().add("name", "Zoe")
                        )
                ))

                // Or: multiple keys
                .add(Arguments.of(
                        or(
                                key("name").eq("Klaus"),
                                key("age").eq(42)
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().add("name", "Klaus"),
                                new Metadata().add("name", "Klaus").add("city", "Munich"),

                                // Or.left is true, Or.right is false
                                new Metadata().add("name", "Klaus").add("age", 666),

                                // only Or.right is present and true
                                new Metadata().add("age", 42),
                                new Metadata().add("age", 42).add("city", "Munich"),

                                // Or.right is true, Or.left is false
                                new Metadata().add("age", 42).add("name", "Alice"),

                                // Or.left and Or.right are both true
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich")
                        ),
                        asList(
                                new Metadata().add("name", "Alice"),
                                new Metadata().add("age", 666),
                                new Metadata().add("name", "Alice").add("age", 666)
                        )
                ))
                .add(Arguments.of(
                        or(
                                key("age").eq(42),
                                key("name").eq("Klaus")
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().add("age", 42),
                                new Metadata().add("age", 42).add("city", "Munich"),

                                // Or.left is true, Or.right is false
                                new Metadata().add("age", 42).add("name", "Alice"),

                                // only Or.right is present and true
                                new Metadata().add("name", "Klaus"),
                                new Metadata().add("name", "Klaus").add("city", "Munich"),

                                // Or.right is true, Or.left is false
                                new Metadata().add("name", "Klaus").add("age", 666),

                                // Or.left and Or.right are both true
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich")
                        ),
                        asList(
                                new Metadata().add("name", "Alice"),
                                new Metadata().add("age", 666),
                                new Metadata().add("name", "Alice").add("age", 666)
                        )
                ))

                // Or: x2
                .add(Arguments.of(
                        or(
                                key("name").eq("Klaus"),
                                or(
                                        key("age").eq(42),
                                        key("city").eq("Munich")
                                )
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().add("name", "Klaus"),
                                new Metadata().add("name", "Klaus").add("country", "Germany"),

                                // Or.left is true, Or.right is false
                                new Metadata().add("name", "Klaus").add("age", 666),
                                new Metadata().add("name", "Klaus").add("city", "Frankfurt"),
                                new Metadata().add("name", "Klaus").add("age", 666).add("city", "Frankfurt"),

                                // only Or.right is present and true
                                new Metadata().add("age", 42),
                                new Metadata().add("age", 42).add("country", "Germany"),
                                new Metadata().add("city", "Munich"),
                                new Metadata().add("city", "Munich").add("country", "Germany"),
                                new Metadata().add("age", 42).add("city", "Munich"),
                                new Metadata().add("age", 42).add("city", "Munich").add("country", "Germany"),

                                // Or.right is true, Or.left is false
                                new Metadata().add("age", 42).add("name", "Alice"),
                                new Metadata().add("city", "Munich").add("name", "Alice"),
                                new Metadata().add("age", 42).add("city", "Munich").add("name", "Alice"),

                                // Or.left and Or.right are both true
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Klaus").add("age", 42).add("country", "Germany"),
                                new Metadata().add("name", "Klaus").add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("city", "Munich").add("country", "Germany"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich").add("country", "Germany")
                        ),
                        asList(
                                new Metadata().add("name", "Alice"),
                                new Metadata().add("age", 666),
                                new Metadata().add("city", "Frankfurt"),
                                new Metadata().add("name", "Alice").add("age", 666),
                                new Metadata().add("name", "Alice").add("city", "Frankfurt"),
                                new Metadata().add("name", "Alice").add("age", 666).add("city", "Frankfurt")
                        )
                ))
                .add(Arguments.of(
                        or(
                                or(
                                        key("name").eq("Klaus"),
                                        key("age").eq(42)
                                ),
                                key("city").eq("Munich")
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().add("name", "Klaus"),
                                new Metadata().add("name", "Klaus").add("country", "Germany"),
                                new Metadata().add("age", 42),
                                new Metadata().add("age", 42).add("country", "Germany"),
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Klaus").add("age", 42).add("country", "Germany"),

                                // Or.left is true, Or.right is false
                                new Metadata().add("name", "Klaus").add("city", "Frankfurt"),
                                new Metadata().add("age", 42).add("city", "Frankfurt"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Frankfurt"),

                                // only Or.right is present and true
                                new Metadata().add("city", "Munich"),
                                new Metadata().add("city", "Munich").add("country", "Germany"),

                                // Or.right is true, Or.left is false
                                new Metadata().add("city", "Munich").add("name", "Alice"),
                                new Metadata().add("city", "Munich").add("age", 666),

                                // Or.left and Or.right are both true
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Klaus").add("age", 42).add("country", "Germany"),
                                new Metadata().add("name", "Klaus").add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("city", "Munich").add("country", "Germany"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich").add("country", "Germany")
                        ),
                        asList(
                                new Metadata().add("name", "Alice"),
                                new Metadata().add("age", 666),
                                new Metadata().add("city", "Frankfurt"),
                                new Metadata().add("name", "Alice").add("age", 666),
                                new Metadata().add("name", "Alice").add("city", "Frankfurt"),
                                new Metadata().add("name", "Alice").add("age", 666).add("city", "Frankfurt")
                        )
                ))

                // === AND ===

                .add(Arguments.of(
                        and(
                                key("name").eq("Klaus"),
                                key("age").eq(42)
                        ),
                        asList(
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().add("name", "Klaus"),

                                // And.left is true, And.right is false
                                new Metadata().add("name", "Klaus").add("age", 666),

                                // only And.right is present and true
                                new Metadata().add("age", 42),

                                // And.right is true, And.left is false
                                new Metadata().add("age", 42).add("name", "Alice"),

                                // And.left, And.right are both false
                                new Metadata().add("age", 666).add("name", "Alice")
                        )
                ))
                .add(Arguments.of(
                        and(
                                key("age").eq(42),
                                key("name").eq("Klaus")
                        ),
                        asList(
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().add("age", 42),

                                // And.left is true, And.right is false
                                new Metadata().add("age", 42).add("name", "Alice"),

                                // only And.right is present and true
                                new Metadata().add("name", "Klaus"),

                                // And.right is true, And.left is false
                                new Metadata().add("name", "Klaus").add("age", 666),

                                // And.left, And.right are both false
                                new Metadata().add("age", 666).add("name", "Alice")
                        )
                ))

                // And: x2
                .add(Arguments.of(
                        and(
                                key("name").eq("Klaus"),
                                and(
                                        key("age").eq(42),
                                        key("city").eq("Munich")
                                )
                        ),
                        asList(
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich").add("country", "Germany")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().add("name", "Klaus"),

                                // And.left is true, And.right is false
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Klaus").add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("age", 666).add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Frankfurt"),

                                // only And.right is present and true
                                new Metadata().add("age", 42).add("city", "Munich"),

                                // And.right is true, And.left is false
                                new Metadata().add("age", 42).add("city", "Munich").add("name", "Alice")
                        )
                ))
                .add(Arguments.of(
                        and(
                                and(
                                        key("name").eq("Klaus"),
                                        key("age").eq(42)
                                ),
                                key("city").eq("Munich")
                        ),
                        asList(
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich").add("country", "Germany")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().add("name", "Klaus").add("age", 42),

                                // And.left is true, And.right is false
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Frankfurt"),

                                // only And.right is present and true
                                new Metadata().add("city", "Munich"),

                                // And.right is true, And.left is false
                                new Metadata().add("city", "Munich").add("name", "Klaus"),
                                new Metadata().add("city", "Munich").add("name", "Klaus").add("age", 666),
                                new Metadata().add("city", "Munich").add("age", 42),
                                new Metadata().add("city", "Munich").add("age", 42).add("name", "Alice")
                        )
                ))

                // === AND + nested OR ===

                .add(Arguments.of(
                        and(
                                key("name").eq("Klaus"),
                                or(
                                        key("age").eq(42),
                                        key("city").eq("Munich")
                                )
                        ),
                        asList(
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Klaus").add("age", 42).add("country", "Germany"),
                                new Metadata().add("name", "Klaus").add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("city", "Munich").add("country", "Germany"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich").add("country", "Germany")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().add("name", "Klaus"),

                                // And.left is true, And.right is false
                                new Metadata().add("name", "Klaus").add("age", 666),
                                new Metadata().add("name", "Klaus").add("city", "Frankfurt"),

                                // only And.right is present and true
                                new Metadata().add("age", 42),
                                new Metadata().add("city", "Munich"),
                                new Metadata().add("age", 42).add("city", "Munich"),

                                // And.right is true, And.left is false
                                new Metadata().add("age", 42).add("name", "Alice"),
                                new Metadata().add("city", "Munich").add("name", "Alice"),
                                new Metadata().add("age", 42).add("city", "Munich").add("name", "Alice")
                        )
                ))
                .add(Arguments.of(
                        and(
                                or(
                                        key("name").eq("Klaus"),
                                        key("age").eq(42)
                                ),
                                key("city").eq("Munich")
                        ),
                        asList(
                                new Metadata().add("name", "Klaus").add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("city", "Munich").add("country", "Germany"),
                                new Metadata().add("age", 42).add("city", "Munich"),
                                new Metadata().add("age", 42).add("city", "Munich").add("country", "Germany"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich").add("country", "Germany")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().add("name", "Klaus"),
                                new Metadata().add("age", 42),
                                new Metadata().add("name", "Klaus").add("age", 42),

                                // And.left is true, And.right is false
                                new Metadata().add("name", "Klaus").add("city", "Frankfurt"),
                                new Metadata().add("age", 42).add("city", "Frankfurt"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Frankfurt"),

                                // only And.right is present and true
                                new Metadata().add("city", "Munich"),

                                // And.right is true, And.left is false
                                new Metadata().add("city", "Munich").add("name", "Alice"),
                                new Metadata().add("city", "Munich").add("age", 666),
                                new Metadata().add("city", "Munich").add("name", "Alice").add("age", 666)
                        )
                ))

                // === OR + nested AND ===
                .add(Arguments.of(
                        or(
                                key("name").eq("Klaus"),
                                and(
                                        key("age").eq(42),
                                        key("city").eq("Munich")
                                )
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().add("name", "Klaus"),
                                new Metadata().add("name", "Klaus").add("country", "Germany"),

                                // Or.left is true, Or.right is false
                                new Metadata().add("name", "Klaus").add("age", 666),
                                new Metadata().add("name", "Klaus").add("city", "Frankfurt"),
                                new Metadata().add("name", "Klaus").add("age", 666).add("city", "Frankfurt"),

                                // only Or.right is present and true
                                new Metadata().add("age", 42).add("city", "Munich"),
                                new Metadata().add("age", 42).add("city", "Munich").add("country", "Germany"),

                                // Or.right is true, Or.left is false
                                new Metadata().add("age", 42).add("city", "Munich").add("name", "Alice")
                        ),
                        asList(
                                new Metadata().add("name", "Alice"),
                                new Metadata().add("age", 666),
                                new Metadata().add("city", "Frankfurt"),
                                new Metadata().add("name", "Alice").add("age", 666).add("city", "Frankfurt")
                        )
                ))
                .add(Arguments.of(
                        or(
                                and(
                                        key("name").eq("Klaus"),
                                        key("age").eq(42)
                                ),
                                key("city").eq("Munich")
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Klaus").add("age", 42).add("country", "Germany"),

                                // Or.left is true, Or.right is false
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Frankfurt"),

                                // only Or.right is present and true
                                new Metadata().add("city", "Munich"),
                                new Metadata().add("city", "Munich").add("country", "Germany"),

                                // Or.right is true, Or.left is true
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich").add("country", "Germany")
                        ),
                        asList(
                                new Metadata().add("name", "Alice"),
                                new Metadata().add("age", 666),
                                new Metadata().add("city", "Frankfurt"),
                                new Metadata().add("name", "Alice").add("age", 666).add("city", "Frankfurt")
                        )
                ))

                // === GROUP ===

                .add(Arguments.of(
                        or(
                                key("name").eq("Klaus"),
                                group(
                                        or(
                                                key("age").eq(42),
                                                key("city").eq("Munich")
                                        )
                                )
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().add("name", "Klaus"),
                                new Metadata().add("name", "Klaus").add("country", "Germany"),

                                // Or.left is true, Or.right is false
                                new Metadata().add("name", "Klaus").add("age", 666),
                                new Metadata().add("name", "Klaus").add("city", "Frankfurt"),
                                new Metadata().add("name", "Klaus").add("age", 666).add("city", "Frankfurt"),

                                // only Or.right is present and true
                                new Metadata().add("age", 42),
                                new Metadata().add("age", 42).add("country", "Germany"),
                                new Metadata().add("city", "Munich"),
                                new Metadata().add("city", "Munich").add("country", "Germany"),
                                new Metadata().add("age", 42).add("city", "Munich"),
                                new Metadata().add("age", 42).add("city", "Munich").add("country", "Germany"),

                                // Or.right is true, Or.left is false
                                new Metadata().add("age", 42).add("name", "Alice"),
                                new Metadata().add("city", "Munich").add("name", "Alice"),
                                new Metadata().add("age", 42).add("city", "Munich").add("name", "Alice"),

                                // Or.left and Or.right are both true
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Klaus").add("age", 42).add("country", "Germany"),
                                new Metadata().add("name", "Klaus").add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("city", "Munich").add("country", "Germany"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich").add("country", "Germany")
                        ),
                        asList(
                                new Metadata().add("name", "Alice"),
                                new Metadata().add("age", 666),
                                new Metadata().add("city", "Frankfurt"),
                                new Metadata().add("name", "Alice").add("age", 666),
                                new Metadata().add("name", "Alice").add("city", "Frankfurt"),
                                new Metadata().add("name", "Alice").add("age", 666).add("city", "Frankfurt")
                        )
                ))
                .add(Arguments.of(
                        or(
                                group(
                                        or(
                                                key("name").eq("Klaus"),
                                                key("age").eq(42)
                                        )
                                ),
                                key("city").eq("Munich")
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().add("name", "Klaus"),
                                new Metadata().add("name", "Klaus").add("country", "Germany"),
                                new Metadata().add("age", 42),
                                new Metadata().add("age", 42).add("country", "Germany"),
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Klaus").add("age", 42).add("country", "Germany"),

                                // Or.left is true, Or.right is false
                                new Metadata().add("name", "Klaus").add("city", "Frankfurt"),
                                new Metadata().add("age", 42).add("city", "Frankfurt"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Frankfurt"),

                                // only Or.right is present and true
                                new Metadata().add("city", "Munich"),
                                new Metadata().add("city", "Munich").add("country", "Germany"),

                                // Or.right is true, Or.left is false
                                new Metadata().add("city", "Munich").add("name", "Alice"),
                                new Metadata().add("city", "Munich").add("age", 666),

                                // Or.left and Or.right are both true
                                new Metadata().add("name", "Klaus").add("age", 42),
                                new Metadata().add("name", "Klaus").add("age", 42).add("country", "Germany"),
                                new Metadata().add("name", "Klaus").add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("city", "Munich").add("country", "Germany"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich"),
                                new Metadata().add("name", "Klaus").add("age", 42).add("city", "Munich").add("country", "Germany")
                        ),
                        asList(
                                new Metadata().add("name", "Alice"),
                                new Metadata().add("age", 666),
                                new Metadata().add("city", "Frankfurt"),
                                new Metadata().add("name", "Alice").add("age", 666),
                                new Metadata().add("name", "Alice").add("city", "Frankfurt"),
                                new Metadata().add("name", "Alice").add("age", 666).add("city", "Frankfurt")
                        )
                ))

                .build();
    }

    // TODO test list, set, map?

    @ParameterizedTest
    @MethodSource
    void should_filter_by_metadata_not(MetadataFilter metadataFilter,
                                       List<Metadata> matchingMetadatas,
                                       List<Metadata> notMatchingMetadatas) {
        // given
        for (Metadata matchingMetadata : matchingMetadatas) {
            TextSegment matchingSegment = TextSegment.from("matching", matchingMetadata);
            Embedding matchingEmbedding = embeddingModel().embed(matchingSegment).content();
            embeddingStore().add(matchingEmbedding, matchingSegment);
        }

        for (Metadata notMatchingMetadata : notMatchingMetadatas) {
            TextSegment notMatchingSegment = TextSegment.from("not matching", notMatchingMetadata);
            Embedding notMatchingEmbedding = embeddingModel().embed(notMatchingSegment).content();
            embeddingStore().add(notMatchingEmbedding, notMatchingSegment);
        }

        TextSegment notMatchingSegmentWithoutMetadata = TextSegment.from("matching");
        Embedding notMatchingWithoutMetadataEmbedding = embeddingModel().embed(notMatchingSegmentWithoutMetadata).content();
        embeddingStore().add(notMatchingWithoutMetadataEmbedding, notMatchingSegmentWithoutMetadata);

        awaitUntilPersisted();

        Embedding queryEmbedding = embeddingModel().embed("matching").content();

        assertThat(embeddingStore().findRelevant(queryEmbedding, 100))
                .hasSize(matchingMetadatas.size() + notMatchingMetadatas.size() + 1);

        SearchRequest searchRequest = SearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .metadataFilter(metadataFilter)
                .maxResults(100)
                .build();

        // when
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore().search(searchRequest);

        // then
        assertThat(matches).hasSize(matchingMetadatas.size() + 1); // +1 for notMatchingSegmentWithoutMetadata
        HashSet<Metadata> expected = new HashSet<>(matchingMetadatas);
        expected.add(new Metadata()); // for notMatchingSegmentWithoutMetadata
        assertThat(matches.stream().map(match -> match.embedded().metadata()).collect(toSet())).isEqualTo(expected);
        matches.forEach(match -> assertThat(match.embedded().text()).isEqualTo("matching"));
        matches.forEach(match -> assertThat(match.score()).isCloseTo(1, withPercentage(0.01)));
        // TODO fix int/long float/double types assertThat(matches.get(0).embedded()).isEqualTo(matchingSegment);
    }

    static Stream<Arguments> should_filter_by_metadata_not() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(
                        not(
                                key("name").eq("Klaus")
                        ),
                        asList(
                                new Metadata().add("name", "Alice"),
                                new Metadata().add("age", 42)
                        ),
                        asList(
                                new Metadata().add("name", "Klaus"),
                                new Metadata().add("name", "Klaus").add("age", 42)
                        )
                ))
                .build();
    }
}
