package dev.langchain4j.store.embedding;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.store.embedding.filter.Filter.*;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

/**
 * A minimum set of tests that each implementation of {@link EmbeddingStore} that supports {@link Filter} must pass.
 */
public abstract class EmbeddingStoreWithFilteringIT extends EmbeddingStoreIT {

    @ParameterizedTest
    @MethodSource
    void should_filter_by_metadata(Filter metadataFilter,
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

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(100)
                .build();
        assertThat(embeddingStore().search(request).matches())
                // +1 for notMatchingSegmentWithoutMetadata
                .hasSize(matchingMetadatas.size() + notMatchingMetadatas.size() + 1);

        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(metadataFilter)
                .maxResults(100)
                .build();

        // when
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore().search(embeddingSearchRequest).matches();

        // then
        assertThat(matches).hasSize(matchingMetadatas.size());
        matches.forEach(match -> assertThat(match.embedded().text()).isEqualTo("matching"));
        matches.forEach(match -> assertThat(match.score()).isCloseTo(1, withPercentage(0.01)));
    }

    static Stream<Arguments> should_filter_by_metadata() {
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
                                new Metadata().put("key2", "a")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isEqualTo(1),
                        asList(
                                new Metadata().put("key", 1),
                                new Metadata().put("key", 1).put("key2", 0)
                        ),
                        asList(
                                new Metadata().put("key", -1),
                                new Metadata().put("key", 0),
                                new Metadata().put("key2", 1)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isEqualTo(1L),
                        asList(
                                new Metadata().put("key", 1L),
                                new Metadata().put("key", 1L).put("key2", 0L)
                        ),
                        asList(
                                new Metadata().put("key", -1L),
                                new Metadata().put("key", 0L),
                                new Metadata().put("key2", 1L)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isEqualTo(1.23f),
                        asList(
                                new Metadata().put("key", 1.23f),
                                new Metadata().put("key", 1.23f).put("key2", 0f)
                        ),
                        asList(
                                new Metadata().put("key", -1.23f),
                                new Metadata().put("key", 1.22f),
                                new Metadata().put("key", 1.24f),
                                new Metadata().put("key2", 1.23f)
                        )
                )).add(Arguments.of(
                        metadataKey("key").isEqualTo(1.23d),
                        asList(
                                new Metadata().put("key", 1.23d),
                                new Metadata().put("key", 1.23d).put("key2", 0d)
                        ),
                        asList(
                                new Metadata().put("key", -1.23d),
                                new Metadata().put("key", 1.22d),
                                new Metadata().put("key", 1.24d),
                                new Metadata().put("key2", 1.23d)
                        )
                ))


                // === GreaterThan ==

                .add(Arguments.of(
                        metadataKey("key").isGreaterThan("b"),
                        asList(
                                new Metadata().put("key", "c"),
                                new Metadata().put("key", "c").put("key2", "a")
                        ),
                        asList(
                                new Metadata().put("key", "a"),
                                new Metadata().put("key", "b"),
                                new Metadata().put("key2", "c")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isGreaterThan(1),
                        asList(
                                new Metadata().put("key", 2),
                                new Metadata().put("key", 2).put("key2", 0)
                        ),
                        asList(
                                new Metadata().put("key", -2),
                                new Metadata().put("key", 0),
                                new Metadata().put("key", 1),
                                new Metadata().put("key2", 2)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isGreaterThan(1L),
                        asList(
                                new Metadata().put("key", 2L),
                                new Metadata().put("key", 2L).put("key2", 0L)
                        ),
                        asList(
                                new Metadata().put("key", -2L),
                                new Metadata().put("key", 0L),
                                new Metadata().put("key", 1L),
                                new Metadata().put("key2", 2L)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isGreaterThan(1.1f),
                        asList(
                                new Metadata().put("key", 1.2f),
                                new Metadata().put("key", 1.2f).put("key2", 1.0f)
                        ),
                        asList(
                                new Metadata().put("key", -1.2f),
                                new Metadata().put("key", 0.0f),
                                new Metadata().put("key", 1.1f),
                                new Metadata().put("key2", 1.2f)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isGreaterThan(1.1d),
                        asList(
                                new Metadata().put("key", 1.2d),
                                new Metadata().put("key", 1.2d).put("key2", 1.0d)
                        ),
                        asList(
                                new Metadata().put("key", -1.2d),
                                new Metadata().put("key", 0.0d),
                                new Metadata().put("key", 1.1d),
                                new Metadata().put("key2", 1.2d)
                        )
                ))


                // === GreaterThanOrEqual ==

                .add(Arguments.of(
                        metadataKey("key").isGreaterThanOrEqualTo("b"),
                        asList(
                                new Metadata().put("key", "b"),
                                new Metadata().put("key", "c"),
                                new Metadata().put("key", "c").put("key2", "a")
                        ),
                        asList(
                                new Metadata().put("key", "a"),
                                new Metadata().put("key2", "b")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isGreaterThanOrEqualTo(1),
                        asList(
                                new Metadata().put("key", 1),
                                new Metadata().put("key", 2),
                                new Metadata().put("key", 2).put("key2", 0)
                        ),
                        asList(
                                new Metadata().put("key", -2),
                                new Metadata().put("key", -1),
                                new Metadata().put("key", 0),
                                new Metadata().put("key2", 1),
                                new Metadata().put("key2", 2)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isGreaterThanOrEqualTo(1L),
                        asList(
                                new Metadata().put("key", 1L),
                                new Metadata().put("key", 2L),
                                new Metadata().put("key", 2L).put("key2", 0L)
                        ),
                        asList(
                                new Metadata().put("key", -2L),
                                new Metadata().put("key", -1L),
                                new Metadata().put("key", 0L),
                                new Metadata().put("key2", 1L),
                                new Metadata().put("key2", 2L)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isGreaterThanOrEqualTo(1.1f),
                        asList(
                                new Metadata().put("key", 1.1f),
                                new Metadata().put("key", 1.2f),
                                new Metadata().put("key", 1.2f).put("key2", 1.0f)
                        ),
                        asList(
                                new Metadata().put("key", -1.2f),
                                new Metadata().put("key", -1.1f),
                                new Metadata().put("key", 0.0f),
                                new Metadata().put("key2", 1.1f),
                                new Metadata().put("key2", 1.2f)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isGreaterThanOrEqualTo(1.1d),
                        asList(
                                new Metadata().put("key", 1.1d),
                                new Metadata().put("key", 1.2d),
                                new Metadata().put("key", 1.2d).put("key2", 1.0d)
                        ),
                        asList(
                                new Metadata().put("key", -1.2d),
                                new Metadata().put("key", -1.1d),
                                new Metadata().put("key", 0.0d),
                                new Metadata().put("key2", 1.1d),
                                new Metadata().put("key2", 1.2d)
                        )
                ))


                // === LessThan ==

                .add(Arguments.of(
                        metadataKey("key").isLessThan("b"),
                        asList(

                                new Metadata().put("key", "a"),
                                new Metadata().put("key", "a").put("key2", "c")
                        ),
                        asList(
                                new Metadata().put("key", "b"),
                                new Metadata().put("key", "c"),
                                new Metadata().put("key2", "a")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isLessThan(1),
                        asList(
                                new Metadata().put("key", -2),
                                new Metadata().put("key", 0),
                                new Metadata().put("key", 0).put("key2", 2)
                        ),
                        asList(
                                new Metadata().put("key", 1),
                                new Metadata().put("key", 2),
                                new Metadata().put("key2", 0)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isLessThan(1L),
                        asList(
                                new Metadata().put("key", -2L),
                                new Metadata().put("key", 0L),
                                new Metadata().put("key", 0L).put("key2", 2L)
                        ),
                        asList(
                                new Metadata().put("key", 1L),
                                new Metadata().put("key", 2L),
                                new Metadata().put("key2", 0L)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isLessThan(1.1f),
                        asList(
                                new Metadata().put("key", -1.2f),
                                new Metadata().put("key", 1.0f),
                                new Metadata().put("key", 1.0f).put("key2", 1.2f)
                        ),
                        asList(
                                new Metadata().put("key", 1.1f),
                                new Metadata().put("key", 1.2f),
                                new Metadata().put("key2", 1.0f)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isLessThan(1.1d),
                        asList(
                                new Metadata().put("key", -1.2d),
                                new Metadata().put("key", 1.0d),
                                new Metadata().put("key", 1.0d).put("key2", 1.2d)
                        ),
                        asList(
                                new Metadata().put("key", 1.1d),
                                new Metadata().put("key", 1.2d),
                                new Metadata().put("key2", 1.0d)
                        )
                ))


                // === LessThanOrEqual ==

                .add(Arguments.of(
                        metadataKey("key").isLessThanOrEqualTo("b"),
                        asList(

                                new Metadata().put("key", "a"),
                                new Metadata().put("key", "b"),
                                new Metadata().put("key", "b").put("key2", "c")
                        ),
                        asList(
                                new Metadata().put("key", "c"),
                                new Metadata().put("key2", "a")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isLessThanOrEqualTo(1),
                        asList(
                                new Metadata().put("key", -2),
                                new Metadata().put("key", 0),
                                new Metadata().put("key", 1),
                                new Metadata().put("key", 1).put("key2", 2)
                        ),
                        asList(
                                new Metadata().put("key", 2),
                                new Metadata().put("key2", 0)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isLessThanOrEqualTo(1L),
                        asList(
                                new Metadata().put("key", -2L),
                                new Metadata().put("key", 0L),
                                new Metadata().put("key", 1L),
                                new Metadata().put("key", 1L).put("key2", 2L)
                        ),
                        asList(
                                new Metadata().put("key", 2L),
                                new Metadata().put("key2", 0L)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isLessThanOrEqualTo(1.1f),
                        asList(
                                new Metadata().put("key", -1.2f),
                                new Metadata().put("key", 1.0f),
                                new Metadata().put("key", 1.1f),
                                new Metadata().put("key", 1.1f).put("key2", 1.2f)
                        ),
                        asList(
                                new Metadata().put("key", 1.2f),
                                new Metadata().put("key2", 1.0f)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isLessThanOrEqualTo(1.1d),
                        asList(
                                new Metadata().put("key", -1.2d),
                                new Metadata().put("key", 1.0d),
                                new Metadata().put("key", 1.1d),
                                new Metadata().put("key", 1.1d).put("key2", 1.2d)
                        ),
                        asList(
                                new Metadata().put("key", 1.2d),
                                new Metadata().put("key2", 1.0d)
                        )
                ))


                // === In ===

                // In: string
                .add(Arguments.of(
                        metadataKey("name").isIn("Klaus"),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("age", 42)
                        ),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name2", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("name").isIn(singletonList("Klaus")),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("age", 42)
                        ),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name2", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("name").isIn("Klaus", "Alice"),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name", "Alice").put("age", 42)
                        ),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Zoe"),
                                new Metadata().put("name2", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("name").isIn(asList("Klaus", "Alice")),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name", "Alice").put("age", 42)
                        ),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Zoe"),
                                new Metadata().put("name2", "Klaus")
                        )
                ))

                // In: integer
                .add(Arguments.of(
                        metadataKey("age").isIn(42),
                        asList(
                                new Metadata().put("age", 42),
                                new Metadata().put("age", 42).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666),
                                new Metadata().put("age2", 42)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isIn(singletonList(42)),
                        asList(
                                new Metadata().put("age", 42),
                                new Metadata().put("age", 42).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666),
                                new Metadata().put("age2", 42)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isIn(42, 18),
                        asList(
                                new Metadata().put("age", 42),
                                new Metadata().put("age", 18),
                                new Metadata().put("age", 42).put("name", "Klaus"),
                                new Metadata().put("age", 18).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666),
                                new Metadata().put("age2", 42)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isIn(asList(42, 18)),
                        asList(
                                new Metadata().put("age", 42),
                                new Metadata().put("age", 18),
                                new Metadata().put("age", 42).put("name", "Klaus"),
                                new Metadata().put("age", 18).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666),
                                new Metadata().put("age2", 42)
                        )
                ))

                // In: long
                .add(Arguments.of(
                        metadataKey("age").isIn(42L),
                        asList(
                                new Metadata().put("age", 42L),
                                new Metadata().put("age", 42L).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666L),
                                new Metadata().put("age2", 42L)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isIn(singletonList(42L)),
                        asList(
                                new Metadata().put("age", 42L),
                                new Metadata().put("age", 42L).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666L),
                                new Metadata().put("age2", 42L)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isIn(42L, 18L),
                        asList(
                                new Metadata().put("age", 42L),
                                new Metadata().put("age", 18L),
                                new Metadata().put("age", 42L).put("name", "Klaus"),
                                new Metadata().put("age", 18L).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666L),
                                new Metadata().put("age2", 42L)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isIn(asList(42L, 18L)),
                        asList(
                                new Metadata().put("age", 42L),
                                new Metadata().put("age", 18L),
                                new Metadata().put("age", 42L).put("name", "Klaus"),
                                new Metadata().put("age", 18L).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666L),
                                new Metadata().put("age2", 42L)
                        )
                ))

                // In: float
                .add(Arguments.of(
                        metadataKey("age").isIn(42.0f),
                        asList(
                                new Metadata().put("age", 42.0f),
                                new Metadata().put("age", 42.0f).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666.0f),
                                new Metadata().put("age2", 42.0f)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isIn(singletonList(42.0f)),
                        asList(
                                new Metadata().put("age", 42.0f),
                                new Metadata().put("age", 42.0f).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666.0f),
                                new Metadata().put("age2", 42.0f)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isIn(42.0f, 18.0f),
                        asList(
                                new Metadata().put("age", 42.0f),
                                new Metadata().put("age", 18.0f),
                                new Metadata().put("age", 42.0f).put("name", "Klaus"),
                                new Metadata().put("age", 18.0f).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666.0f),
                                new Metadata().put("age2", 42.0f)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isIn(asList(42.0f, 18.0f)),
                        asList(
                                new Metadata().put("age", 42.0f),
                                new Metadata().put("age", 18.0f),
                                new Metadata().put("age", 42.0f).put("name", "Klaus"),
                                new Metadata().put("age", 18.0f).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666.0f),
                                new Metadata().put("age2", 42.0f)
                        )
                ))

                // In: double
                .add(Arguments.of(
                        metadataKey("age").isIn(42.0d),
                        asList(
                                new Metadata().put("age", 42.0d),
                                new Metadata().put("age", 42.0d).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666.0d),
                                new Metadata().put("age2", 42.0d)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isIn(singletonList(42.0d)),
                        asList(
                                new Metadata().put("age", 42.0d),
                                new Metadata().put("age", 42.0d).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666.0d),
                                new Metadata().put("age2", 42.0d)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isIn(42.0d, 18.0d),
                        asList(
                                new Metadata().put("age", 42.0d),
                                new Metadata().put("age", 18.0d),
                                new Metadata().put("age", 42.0d).put("name", "Klaus"),
                                new Metadata().put("age", 18.0d).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666.0d),
                                new Metadata().put("age2", 42.0d)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isIn(asList(42.0d, 18.0d)),
                        asList(
                                new Metadata().put("age", 42.0d),
                                new Metadata().put("age", 18.0d),
                                new Metadata().put("age", 42.0d).put("name", "Klaus"),
                                new Metadata().put("age", 18.0d).put("name", "Klaus")
                        ),
                        asList(
                                new Metadata().put("age", 666.0d),
                                new Metadata().put("age2", 42.0d)
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
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name", "Alice").put("age", 42)
                        ),
                        singletonList(
                                new Metadata().put("name", "Zoe")
                        )
                ))
                .add(Arguments.of(
                        or(
                                metadataKey("name").isEqualTo("Alice"),
                                metadataKey("name").isEqualTo("Klaus")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name", "Alice").put("age", 42),
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("age", 42)
                        ),
                        singletonList(
                                new Metadata().put("name", "Zoe")
                        )
                ))

                // Or: multiple keys
                .add(Arguments.of(
                        or(
                                metadataKey("name").isEqualTo("Klaus"),
                                metadataKey("age").isEqualTo(42)
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("city", "Munich"),

                                // Or.left is true, Or.right is false
                                new Metadata().put("name", "Klaus").put("age", 666),

                                // only Or.right is present and true
                                new Metadata().put("age", 42),
                                new Metadata().put("age", 42).put("city", "Munich"),

                                // Or.right is true, Or.left is false
                                new Metadata().put("age", 42).put("name", "Alice"),

                                // Or.left and Or.right are both true
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("age", 666),
                                new Metadata().put("name", "Alice").put("age", 666)
                        )
                ))
                .add(Arguments.of(
                        or(
                                metadataKey("age").isEqualTo(42),
                                metadataKey("name").isEqualTo("Klaus")
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().put("age", 42),
                                new Metadata().put("age", 42).put("city", "Munich"),

                                // Or.left is true, Or.right is false
                                new Metadata().put("age", 42).put("name", "Alice"),

                                // only Or.right is present and true
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("city", "Munich"),

                                // Or.right is true, Or.left is false
                                new Metadata().put("name", "Klaus").put("age", 666),

                                // Or.left and Or.right are both true
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("age", 666),
                                new Metadata().put("name", "Alice").put("age", 666)
                        )
                ))

                // Or: x2
                .add(Arguments.of(
                        or(
                                metadataKey("name").isEqualTo("Klaus"),
                                or(
                                        metadataKey("age").isEqualTo(42),
                                        metadataKey("city").isEqualTo("Munich")
                                )
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("country", "Germany"),

                                // Or.left is true, Or.right is false
                                new Metadata().put("name", "Klaus").put("age", 666),
                                new Metadata().put("name", "Klaus").put("city", "Frankfurt"),
                                new Metadata().put("name", "Klaus").put("age", 666).put("city", "Frankfurt"),

                                // only Or.right is present and true
                                new Metadata().put("age", 42),
                                new Metadata().put("age", 42).put("country", "Germany"),
                                new Metadata().put("city", "Munich"),
                                new Metadata().put("city", "Munich").put("country", "Germany"),
                                new Metadata().put("age", 42).put("city", "Munich"),
                                new Metadata().put("age", 42).put("city", "Munich").put("country", "Germany"),

                                // Or.right is true, Or.left is false
                                new Metadata().put("age", 42).put("name", "Alice"),
                                new Metadata().put("city", "Munich").put("name", "Alice"),
                                new Metadata().put("age", 42).put("city", "Munich").put("name", "Alice"),

                                // Or.left and Or.right are both true
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Klaus").put("age", 42).put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("city", "Munich").put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich").put("country", "Germany")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("age", 666),
                                new Metadata().put("city", "Frankfurt"),
                                new Metadata().put("name", "Alice").put("age", 666),
                                new Metadata().put("name", "Alice").put("city", "Frankfurt"),
                                new Metadata().put("name", "Alice").put("age", 666).put("city", "Frankfurt")
                        )
                ))
                .add(Arguments.of(
                        or(
                                or(
                                        metadataKey("name").isEqualTo("Klaus"),
                                        metadataKey("age").isEqualTo(42)
                                ),
                                metadataKey("city").isEqualTo("Munich")
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("country", "Germany"),
                                new Metadata().put("age", 42),
                                new Metadata().put("age", 42).put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Klaus").put("age", 42).put("country", "Germany"),

                                // Or.left is true, Or.right is false
                                new Metadata().put("name", "Klaus").put("city", "Frankfurt"),
                                new Metadata().put("age", 42).put("city", "Frankfurt"),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Frankfurt"),

                                // only Or.right is present and true
                                new Metadata().put("city", "Munich"),
                                new Metadata().put("city", "Munich").put("country", "Germany"),

                                // Or.right is true, Or.left is false
                                new Metadata().put("city", "Munich").put("name", "Alice"),
                                new Metadata().put("city", "Munich").put("age", 666),

                                // Or.left and Or.right are both true
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Klaus").put("age", 42).put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("city", "Munich").put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich").put("country", "Germany")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("age", 666),
                                new Metadata().put("city", "Frankfurt"),
                                new Metadata().put("name", "Alice").put("age", 666),
                                new Metadata().put("name", "Alice").put("city", "Frankfurt"),
                                new Metadata().put("name", "Alice").put("age", 666).put("city", "Frankfurt")
                        )
                ))

                // === AND ===

                .add(Arguments.of(
                        and(
                                metadataKey("name").isEqualTo("Klaus"),
                                metadataKey("age").isEqualTo(42)
                        ),
                        asList(
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().put("name", "Klaus"),

                                // And.left is true, And.right is false
                                new Metadata().put("name", "Klaus").put("age", 666),

                                // only And.right is present and true
                                new Metadata().put("age", 42),

                                // And.right is true, And.left is false
                                new Metadata().put("age", 42).put("name", "Alice"),

                                // And.left, And.right are both false
                                new Metadata().put("age", 666).put("name", "Alice")
                        )
                ))
                .add(Arguments.of(
                        and(
                                metadataKey("age").isEqualTo(42),
                                metadataKey("name").isEqualTo("Klaus")
                        ),
                        asList(
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().put("age", 42),

                                // And.left is true, And.right is false
                                new Metadata().put("age", 42).put("name", "Alice"),

                                // only And.right is present and true
                                new Metadata().put("name", "Klaus"),

                                // And.right is true, And.left is false
                                new Metadata().put("name", "Klaus").put("age", 666),

                                // And.left, And.right are both false
                                new Metadata().put("age", 666).put("name", "Alice")
                        )
                ))

                // And: x2
                .add(Arguments.of(
                        and(
                                metadataKey("name").isEqualTo("Klaus"),
                                and(
                                        metadataKey("age").isEqualTo(42),
                                        metadataKey("city").isEqualTo("Munich")
                                )
                        ),
                        asList(
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich").put("country", "Germany")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().put("name", "Klaus"),

                                // And.left is true, And.right is false
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Klaus").put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("age", 666).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Frankfurt"),

                                // only And.right is present and true
                                new Metadata().put("age", 42).put("city", "Munich"),

                                // And.right is true, And.left is false
                                new Metadata().put("age", 42).put("city", "Munich").put("name", "Alice")
                        )
                ))
                .add(Arguments.of(
                        and(
                                and(
                                        metadataKey("name").isEqualTo("Klaus"),
                                        metadataKey("age").isEqualTo(42)
                                ),
                                metadataKey("city").isEqualTo("Munich")
                        ),
                        asList(
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich").put("country", "Germany")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().put("name", "Klaus").put("age", 42),

                                // And.left is true, And.right is false
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Frankfurt"),

                                // only And.right is present and true
                                new Metadata().put("city", "Munich"),

                                // And.right is true, And.left is false
                                new Metadata().put("city", "Munich").put("name", "Klaus"),
                                new Metadata().put("city", "Munich").put("name", "Klaus").put("age", 666),
                                new Metadata().put("city", "Munich").put("age", 42),
                                new Metadata().put("city", "Munich").put("age", 42).put("name", "Alice")
                        )
                ))

                // === AND + nested OR ===

                .add(Arguments.of(
                        and(
                                metadataKey("name").isEqualTo("Klaus"),
                                or(
                                        metadataKey("age").isEqualTo(42),
                                        metadataKey("city").isEqualTo("Munich")
                                )
                        ),
                        asList(
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Klaus").put("age", 42).put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("city", "Munich").put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich").put("country", "Germany")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().put("name", "Klaus"),

                                // And.left is true, And.right is false
                                new Metadata().put("name", "Klaus").put("age", 666),
                                new Metadata().put("name", "Klaus").put("city", "Frankfurt"),

                                // only And.right is present and true
                                new Metadata().put("age", 42),
                                new Metadata().put("city", "Munich"),
                                new Metadata().put("age", 42).put("city", "Munich"),

                                // And.right is true, And.left is false
                                new Metadata().put("age", 42).put("name", "Alice"),
                                new Metadata().put("city", "Munich").put("name", "Alice"),
                                new Metadata().put("age", 42).put("city", "Munich").put("name", "Alice")
                        )
                ))
                .add(Arguments.of(
                        and(
                                or(
                                        metadataKey("name").isEqualTo("Klaus"),
                                        metadataKey("age").isEqualTo(42)
                                ),
                                metadataKey("city").isEqualTo("Munich")
                        ),
                        asList(
                                new Metadata().put("name", "Klaus").put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("city", "Munich").put("country", "Germany"),
                                new Metadata().put("age", 42).put("city", "Munich"),
                                new Metadata().put("age", 42).put("city", "Munich").put("country", "Germany"),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich").put("country", "Germany")
                        ),
                        asList(
                                // only And.left is present and true
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("age", 42),
                                new Metadata().put("name", "Klaus").put("age", 42),

                                // And.left is true, And.right is false
                                new Metadata().put("name", "Klaus").put("city", "Frankfurt"),
                                new Metadata().put("age", 42).put("city", "Frankfurt"),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Frankfurt"),

                                // only And.right is present and true
                                new Metadata().put("city", "Munich"),

                                // And.right is true, And.left is false
                                new Metadata().put("city", "Munich").put("name", "Alice"),
                                new Metadata().put("city", "Munich").put("age", 666),
                                new Metadata().put("city", "Munich").put("name", "Alice").put("age", 666)
                        )
                ))

                // === OR + nested AND ===
                .add(Arguments.of(
                        or(
                                metadataKey("name").isEqualTo("Klaus"),
                                and(
                                        metadataKey("age").isEqualTo(42),
                                        metadataKey("city").isEqualTo("Munich")
                                )
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("country", "Germany"),

                                // Or.left is true, Or.right is false
                                new Metadata().put("name", "Klaus").put("age", 666),
                                new Metadata().put("name", "Klaus").put("city", "Frankfurt"),
                                new Metadata().put("name", "Klaus").put("age", 666).put("city", "Frankfurt"),

                                // only Or.right is present and true
                                new Metadata().put("age", 42).put("city", "Munich"),
                                new Metadata().put("age", 42).put("city", "Munich").put("country", "Germany"),

                                // Or.right is true, Or.left is false
                                new Metadata().put("age", 42).put("city", "Munich").put("name", "Alice")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("age", 666),
                                new Metadata().put("city", "Frankfurt"),
                                new Metadata().put("name", "Alice").put("age", 666).put("city", "Frankfurt")
                        )
                ))
                .add(Arguments.of(
                        or(
                                and(
                                        metadataKey("name").isEqualTo("Klaus"),
                                        metadataKey("age").isEqualTo(42)
                                ),
                                metadataKey("city").isEqualTo("Munich")
                        ),
                        asList(
                                // only Or.left is present and true
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Klaus").put("age", 42).put("country", "Germany"),

                                // Or.left is true, Or.right is false
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Frankfurt"),

                                // only Or.right is present and true
                                new Metadata().put("city", "Munich"),
                                new Metadata().put("city", "Munich").put("country", "Germany"),

                                // Or.right is true, Or.left is true
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich"),
                                new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich").put("country", "Germany")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("age", 666),
                                new Metadata().put("city", "Frankfurt"),
                                new Metadata().put("name", "Alice").put("age", 666).put("city", "Frankfurt")
                        )
                ))

                .build();
    }

    @ParameterizedTest
    @MethodSource
    void should_filter_by_metadata_not(Filter metadataFilter,
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
                // +1 for notMatchingSegmentWithoutMetadata
                .hasSize(matchingMetadatas.size() + notMatchingMetadatas.size() + 1);

        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(metadataFilter)
                .maxResults(100)
                .build();

        // when
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore().search(embeddingSearchRequest).matches();

        // then
        assertThat(matches).hasSize(matchingMetadatas.size() + 1); // +1 for notMatchingSegmentWithoutMetadata
        matches.forEach(match -> assertThat(match.embedded().text()).isEqualTo("matching"));
        matches.forEach(match -> assertThat(match.score()).isCloseTo(1, withPercentage(0.01)));
    }

    static Stream<Arguments> should_filter_by_metadata_not() {
        return Stream.<Arguments>builder()

                // === Not ===
                .add(Arguments.of(
                        not(
                                metadataKey("name").isEqualTo("Klaus")
                        ),
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("age", 42)
                        ),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("age", 42)
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
                                new Metadata().put("key2", "a")
                        ),
                        asList(
                                new Metadata().put("key", "a"),
                                new Metadata().put("key", "a").put("key2", "b")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isNotEqualTo(1),
                        asList(
                                new Metadata().put("key", -1),
                                new Metadata().put("key", 0),
                                new Metadata().put("key", 2),
                                new Metadata().put("key", 10),
                                new Metadata().put("key2", 1)
                        ),
                        asList(
                                new Metadata().put("key", 1),
                                new Metadata().put("key", 1).put("key2", 2)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isNotEqualTo(1L),
                        asList(
                                new Metadata().put("key", -1L),
                                new Metadata().put("key", 0L),
                                new Metadata().put("key", 2L),
                                new Metadata().put("key", 10L),
                                new Metadata().put("key2", 1L)
                        ),
                        asList(
                                new Metadata().put("key", 1L),
                                new Metadata().put("key", 1L).put("key2", 2L)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isNotEqualTo(1.1f),
                        asList(
                                new Metadata().put("key", -1.1f),
                                new Metadata().put("key", 0.0f),
                                new Metadata().put("key", 1.11f),
                                new Metadata().put("key", 2.2f),
                                new Metadata().put("key2", 1.1f)
                        ),
                        asList(
                                new Metadata().put("key", 1.1f),
                                new Metadata().put("key", 1.1f).put("key2", 2.2f)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("key").isNotEqualTo(1.1),
                        asList(
                                new Metadata().put("key", -1.1),
                                new Metadata().put("key", 0.0),
                                new Metadata().put("key", 1.11),
                                new Metadata().put("key", 2.2),
                                new Metadata().put("key2", 1.1)
                        ),
                        asList(
                                new Metadata().put("key", 1.1),
                                new Metadata().put("key", 1.1).put("key2", 2.2)
                        )
                ))


                // === NotIn ===

                // NotIn: string
                .add(Arguments.of(
                        metadataKey("name").isNotIn("Klaus"),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name2", "Klaus")
                        ),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("age", 42)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("name").isNotIn(singletonList("Klaus")),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name2", "Klaus")
                        ),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("age", 42)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("name").isNotIn("Klaus", "Alice"),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Zoe"),
                                new Metadata().put("name2", "Klaus")
                        ),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name", "Alice").put("age", 42)
                        )
                ))
                .add(Arguments.of(
                        metadataKey("name").isNotIn(asList("Klaus", "Alice")),
                        asList(
                                new Metadata().put("name", "Klaus Heisler"),
                                new Metadata().put("name", "Zoe"),
                                new Metadata().put("name2", "Klaus")
                        ),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("age", 42),
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("name", "Alice").put("age", 42)
                        )
                ))

                // NotIn: int
                .add(Arguments.of(
                        metadataKey("age").isNotIn(42),
                        asList(
                                new Metadata().put("age", 666),
                                new Metadata().put("age2", 42)
                        ),
                        asList(
                                new Metadata().put("age", 42),
                                new Metadata().put("age", 42).put("name", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isNotIn(singletonList(42)),
                        asList(
                                new Metadata().put("age", 666),
                                new Metadata().put("age2", 42)
                        ),
                        asList(
                                new Metadata().put("age", 42),
                                new Metadata().put("age", 42).put("name", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isNotIn(42, 18),
                        asList(
                                new Metadata().put("age", 666),
                                new Metadata().put("age2", 42)
                        ),
                        asList(
                                new Metadata().put("age", 42),
                                new Metadata().put("age", 18),
                                new Metadata().put("age", 42).put("name", "Klaus"),
                                new Metadata().put("age", 18).put("name", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isNotIn(asList(42, 18)),
                        asList(
                                new Metadata().put("age", 666),
                                new Metadata().put("age2", 42)
                        ),
                        asList(
                                new Metadata().put("age", 42),
                                new Metadata().put("age", 18),
                                new Metadata().put("age", 42).put("name", "Klaus"),
                                new Metadata().put("age", 18).put("name", "Klaus")
                        )
                ))

                // NotIn: long
                .add(Arguments.of(
                        metadataKey("age").isNotIn(42L),
                        asList(
                                new Metadata().put("age", 666L),
                                new Metadata().put("age2", 42L)
                        ),
                        asList(
                                new Metadata().put("age", 42L),
                                new Metadata().put("age", 42L).put("name", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isNotIn(singletonList(42L)),
                        asList(
                                new Metadata().put("age", 666L),
                                new Metadata().put("age2", 42L)
                        ),
                        asList(
                                new Metadata().put("age", 42L),
                                new Metadata().put("age", 42L).put("name", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isNotIn(42L, 18L),
                        asList(
                                new Metadata().put("age", 666L),
                                new Metadata().put("age2", 42L)
                        ),
                        asList(
                                new Metadata().put("age", 42L),
                                new Metadata().put("age", 18L),
                                new Metadata().put("age", 42L).put("name", "Klaus"),
                                new Metadata().put("age", 18L).put("name", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isNotIn(asList(42L, 18L)),
                        asList(
                                new Metadata().put("age", 666L),
                                new Metadata().put("age2", 42L)
                        ),
                        asList(
                                new Metadata().put("age", 42L),
                                new Metadata().put("age", 18L),
                                new Metadata().put("age", 42L).put("name", "Klaus"),
                                new Metadata().put("age", 18L).put("name", "Klaus")
                        )
                ))

                // NotIn: float
                .add(Arguments.of(
                        metadataKey("age").isNotIn(42.0f),
                        asList(
                                new Metadata().put("age", 666.0f),
                                new Metadata().put("age2", 42.0f)
                        ),
                        asList(
                                new Metadata().put("age", 42.0f),
                                new Metadata().put("age", 42.0f).put("name", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isNotIn(singletonList(42.0f)),
                        asList(
                                new Metadata().put("age", 666.0f),
                                new Metadata().put("age2", 42.0f)
                        ),
                        asList(
                                new Metadata().put("age", 42.0f),
                                new Metadata().put("age", 42.0f).put("name", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isNotIn(42.0f, 18.0f),
                        asList(
                                new Metadata().put("age", 666.0f),
                                new Metadata().put("age2", 42.0f)
                        ),
                        asList(
                                new Metadata().put("age", 42.0f),
                                new Metadata().put("age", 18.0f),
                                new Metadata().put("age", 42.0f).put("name", "Klaus"),
                                new Metadata().put("age", 18.0f).put("name", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isNotIn(asList(42.0f, 18.0f)),
                        asList(
                                new Metadata().put("age", 666.0f),
                                new Metadata().put("age2", 42.0f)
                        ),
                        asList(
                                new Metadata().put("age", 42.0f),
                                new Metadata().put("age", 18.0f),
                                new Metadata().put("age", 42.0f).put("name", "Klaus"),
                                new Metadata().put("age", 18.0f).put("name", "Klaus")
                        )
                ))

                // NotIn: double
                .add(Arguments.of(
                        metadataKey("age").isNotIn(42.0d),
                        asList(
                                new Metadata().put("age", 666.0d),
                                new Metadata().put("age2", 42.0d)
                        ),
                        asList(
                                new Metadata().put("age", 42.0d),
                                new Metadata().put("age", 42.0d).put("name", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isNotIn(singletonList(42.0d)),
                        asList(
                                new Metadata().put("age", 666.0d),
                                new Metadata().put("age2", 42.0d)
                        ),
                        asList(
                                new Metadata().put("age", 42.0d),
                                new Metadata().put("age", 42.0d).put("name", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isNotIn(42.0d, 18.0d),
                        asList(
                                new Metadata().put("age", 666.0d),
                                new Metadata().put("age2", 42.0d)
                        ),
                        asList(
                                new Metadata().put("age", 42.0d),
                                new Metadata().put("age", 18.0d),
                                new Metadata().put("age", 42.0d).put("name", "Klaus"),
                                new Metadata().put("age", 18.0d).put("name", "Klaus")
                        )
                ))
                .add(Arguments.of(
                        metadataKey("age").isNotIn(asList(42.0d, 18.0d)),
                        asList(
                                new Metadata().put("age", 666.0d),
                                new Metadata().put("age2", 42.0d)
                        ),
                        asList(
                                new Metadata().put("age", 42.0d),
                                new Metadata().put("age", 18.0d),
                                new Metadata().put("age", 42.0d).put("name", "Klaus"),
                                new Metadata().put("age", 18.0d).put("name", "Klaus")
                        )
                ))

                .build();
    }
}
