package dev.langchain4j.store.embedding.filter.sql;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.MetadataFilter;
import dev.langchain4j.store.embedding.filter.MetadataFilterParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.store.embedding.filter.MetadataFilter.MetadataKey.key;
import static dev.langchain4j.store.embedding.filter.MetadataFilter.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.of;

class SqlMetadataFilterParserTest {

    MetadataFilterParser parser = new SqlMetadataFilterParser();

    @ParameterizedTest
    @MethodSource
    void should_parse(String sqlWhereExpression, MetadataFilter expectedMetadataFilter) {

        // when
        MetadataFilter metadataFilter = parser.parse(sqlWhereExpression);

        // then
        assertThat(metadataFilter).isEqualTo(expectedMetadataFilter);
    }

    static Stream<Arguments> should_parse() {
        return Stream.<Arguments>builder()

                // eq
                .add(of(
                        "name = 'Klaus'",
                        key("name").eq("Klaus")
                ))
                .add(of(
                        "age = 18",
                        key("age").eq(18L)
                ))
                .add(of(
                        "weight = 67.8",
                        key("weight").eq(67.8d)
                ))


                // ne
                .add(of(
                        "name != 'Klaus'",
                        key("name").ne("Klaus")
                ))
                .add(of(
                        "age != 18",
                        key("age").ne(18L)
                ))
                .add(of(
                        "weight != 67.8",
                        key("weight").ne(67.8d)
                ))


                // gt
                .add(of(
                        "name > 'Klaus'",
                        key("name").gt("Klaus")
                ))
                .add(of(
                        "age > 18",
                        key("age").gt(18L)
                ))
                .add(of(
                        "weight > 67.8",
                        key("weight").gt(67.8d)
                ))


                // gte
                .add(of(
                        "name >= 'Klaus'",
                        key("name").gte("Klaus")
                ))
                .add(of(
                        "age >= 18",
                        key("age").gte(18L)
                ))
                .add(of(
                        "weight >= 67.8",
                        key("weight").gte(67.8d)
                ))


                // lt
                .add(of(
                        "name < 'Klaus'",
                        key("name").lt("Klaus")
                ))
                .add(of(
                        "age < 18",
                        key("age").lt(18L)
                ))
                .add(of(
                        "weight < 67.8",
                        key("weight").lt(67.8d)
                ))


                // lte
                .add(of(
                        "name <= 'Klaus'",
                        key("name").lte("Klaus")
                ))
                .add(of(
                        "age <= 18",
                        key("age").lte(18L)
                ))
                .add(of(
                        "weight <= 67.8",
                        key("weight").lte(67.8d)
                ))


                // in
                .add(of(
                        "name IN ('Klaus', 'Francine')",
                        key("name").in("Klaus", "Francine")
                ))
                .add(of(
                        "age IN (18, 42)",
                        key("age").in(18L, 42L)
                ))
                .add(of(
                        "weight IN (67.8, 78.9)",
                        key("weight").in(67.8d, 78.9d)
                ))


                // nin
                .add(of(
                        "name NOT IN ('Klaus', 'Francine')",
                        key("name").nin("Klaus", "Francine")
                ))
                .add(of(
                        "age NOT IN (18, 42)",
                        key("age").nin(18L, 42L)
                ))
                .add(of(
                        "weight NOT IN (67.8, 78.9)",
                        key("weight").nin(67.8d, 78.9d)
                ))


                // and
                .add(of(
                        "name = 'Klaus' AND age = 18",
                        and(
                                key("name").eq("Klaus"),
                                key("age").eq(18L)
                        )
                ))


                // not
                .add(of(
                        "NOT name = 'Klaus'",
                        not(key("name").eq("Klaus"))
                ))
                .add(of(
                        "NOT (name = 'Klaus')",
                        not(key("name").eq("Klaus"))
                ))


                // or
                .add(of(
                        "name = 'Klaus' OR age = 18",
                        or(
                                key("name").eq("Klaus"),
                                key("age").eq(18L)
                        )
                ))


                // or x2
                .add(of(
                        "color = 'white' OR color = 'black' OR color = 'red'",
                        or(
                                or(
                                        key("color").eq("white"),
                                        key("color").eq("black")
                                ),
                                key("color").eq("red")
                        )
                ))
                .add(of(
                        "(color = 'white' OR color = 'black') OR color = 'red'",
                        or(
                                or(
                                        key("color").eq("white"),
                                        key("color").eq("black")
                                ),
                                key("color").eq("red")
                        )
                ))
                .add(of(
                        "color = 'white' OR (color = 'black' OR color = 'red')",
                        or(
                                key("color").eq("white"),
                                or(
                                        key("color").eq("black"),
                                        key("color").eq("red")
                                )
                        )
                ))


                // or + and
                .add(of(
                        "color = 'white' OR color = 'black' AND form = 'circle'",
                        or(
                                key("color").eq("white"),
                                and(
                                        key("color").eq("black"),
                                        key("form").eq("circle")
                                )
                        )
                ))
                .add(of(
                        "(color = 'white' OR color = 'black') AND form = 'circle'",
                        and(
                                or(
                                        key("color").eq("white"),
                                        key("color").eq("black")
                                ),
                                key("form").eq("circle")
                        )
                ))


                // and + or
                .add(of(
                        "color = 'white' AND shape = 'circle' OR color = 'red'",
                        or(
                                and(
                                        key("color").eq("white"),
                                        key("shape").eq("circle")
                                ),
                                key("color").eq("red")
                        )
                ))
                .add(of(
                        "color = 'white' AND (shape = 'circle' OR color = 'red')",
                        and(
                                key("color").eq("white"),
                                or(
                                        key("shape").eq("circle"),
                                        key("color").eq("red")
                                )
                        )
                ))


                // and x2
                .add(of(
                        "color = 'white' AND form = 'circle' AND area > 7",
                        and(
                                and(
                                        key("color").eq("white"),
                                        key("form").eq("circle")
                                ),
                                key("area").gt(7L)
                        )
                ))

                .build();
    }

    @ParameterizedTest
    @MethodSource
    void should_filter_by_metadata(String sqlWhereExpression,
                                   List<Metadata> matchingMetadatas,
                                   List<Metadata> notMatchingMetadatas) {

        MetadataFilter metadataFilter = parser.parse(sqlWhereExpression);

        for (Metadata matchingMetadata : matchingMetadatas) {
            assertThat(metadataFilter.test(matchingMetadata)).isTrue();
        }

        for (Metadata notMatchingMetadata : notMatchingMetadatas) {
            assertThat(metadataFilter.test(notMatchingMetadata)).isFalse();
        }

        Metadata emptyMetadata = new Metadata();
        assertThat(metadataFilter.test(emptyMetadata)).isFalse();
    }

    static Stream<Arguments> should_filter_by_metadata() {
        return Stream.<Arguments>builder()


                // === Equal ===

                .add(Arguments.of(
                        "key = 'a'",
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

                // integer
                .add(Arguments.of(
                        "key = -2147483648",
                        asList(
                                new Metadata().put("key", Integer.MIN_VALUE),
                                new Metadata().put("key", Integer.MIN_VALUE).put("key2", 0)
                        ),
                        asList(
                                new Metadata().put("key", Integer.MIN_VALUE + 1),
                                new Metadata().put("key", 0),
                                new Metadata().put("key", Integer.MAX_VALUE),
                                new Metadata().put("key2", Integer.MIN_VALUE)
                        )

                ))
                .add(Arguments.of(
                        "key = 0",
                        asList(
                                new Metadata().put("key", 0),
                                new Metadata().put("key", 0).put("key2", 1)
                        ),
                        asList(
                                new Metadata().put("key", -1),
                                new Metadata().put("key", 1),
                                new Metadata().put("key2", 0)
                        )
                ))
                .add(Arguments.of(
                        "key = 2147483647",
                        asList(
                                new Metadata().put("key", Integer.MAX_VALUE),
                                new Metadata().put("key", Integer.MAX_VALUE).put("key2", 0)
                        ),
                        asList(
                                new Metadata().put("key", Integer.MIN_VALUE),
                                new Metadata().put("key", 0),
                                new Metadata().put("key", Integer.MAX_VALUE - 1),
                                new Metadata().put("key2", Integer.MAX_VALUE)
                        )
                ))

                // long
                .add(Arguments.of(
                        "key = -9223372036854775808",
                        asList(
                                new Metadata().put("key", Long.MIN_VALUE),
                                new Metadata().put("key", Long.MIN_VALUE).put("key2", 0L)
                        ),
                        asList(
                                new Metadata().put("key", Long.MIN_VALUE + 1L),
                                new Metadata().put("key", 0L),
                                new Metadata().put("key", Long.MAX_VALUE),
                                new Metadata().put("key2", Long.MIN_VALUE)
                        )
                ))
                .add(Arguments.of(
                        "key = 0",
                        asList(
                                new Metadata().put("key", 0L),
                                new Metadata().put("key", 0L).put("key2", 1L)
                        ),
                        asList(
                                new Metadata().put("key", -1L),
                                new Metadata().put("key", 1L),
                                new Metadata().put("key2", 0L)
                        )
                ))
                .add(Arguments.of(
                        "key = 9223372036854775807",
                        asList(
                                new Metadata().put("key", Long.MAX_VALUE),
                                new Metadata().put("key", Long.MAX_VALUE).put("key2", 0L)
                        ),
                        asList(
                                new Metadata().put("key", Long.MIN_VALUE),
                                new Metadata().put("key", 0L),
                                new Metadata().put("key", Long.MAX_VALUE - 1L),
                                new Metadata().put("key2", Long.MAX_VALUE)
                        )
                ))

                // float
                .add(Arguments.of(
                        "key = -340282346638528859811704183484516925440",
                        asList(
                                new Metadata().put("key", -340282346638528859811704183484516925440f),
                                new Metadata().put("key", -340282346638528859811704183484516925440f).put("key2", 0f)
                        ),
                        asList(
                                new Metadata().put("key", Math.nextUp(-Float.MAX_VALUE)),
                                new Metadata().put("key", Float.MIN_VALUE),
                                new Metadata().put("key", Float.MAX_VALUE),
                                new Metadata().put("key2", -340282346638528859811704183484516925440f)
                        )
                ))
                .add(Arguments.of(
                        "key = 0",
                        asList(
                                new Metadata().put("key", 0f),
                                new Metadata().put("key", 0f).put("key2", 1f)
                        ),
                        asList(
                                new Metadata().put("key", Math.nextDown(0f)),
                                new Metadata().put("key", Math.nextUp(0f)),
                                new Metadata().put("key2", 0f)
                        )
                ))
                .add(Arguments.of(
                        "key = 340282346638528859811704183484516925440",
                        asList(
                                new Metadata().put("key", 340282346638528859811704183484516925440f),
                                new Metadata().put("key", 340282346638528859811704183484516925440f).put("key2", 0f)
                        ),
                        asList(
                                new Metadata().put("key", -Float.MAX_VALUE),
                                new Metadata().put("key", Float.MIN_VALUE),
                                new Metadata().put("key", Math.nextDown(Float.MAX_VALUE)),
                                new Metadata().put("key2", 340282346638528859811704183484516925440f)
                        )
                ))

                // double
                .add(Arguments.of(
                        key("key").eq(-Double.MAX_VALUE),
                        asList(
                                new Metadata().put("key", -Double.MAX_VALUE),
                                new Metadata().put("key", -Double.MAX_VALUE).put("key2", 0d)
                        ),
                        asList(
                                new Metadata().put("key", Math.nextUp(-Double.MAX_VALUE)),
                                new Metadata().put("key", Double.MIN_VALUE),
                                new Metadata().put("key", Double.MAX_VALUE),
                                new Metadata().put("key2", -Double.MAX_VALUE)
                        )
                ))
                .add(Arguments.of(
                        "key = 0",
                        asList(
                                new Metadata().put("key", 0d),
                                new Metadata().put("key", 0d).put("key2", 1d)
                        ),
                        asList(
                                new Metadata().put("key", Math.nextDown(0d)),
                                new Metadata().put("key", Math.nextUp(0d)),
                                new Metadata().put("key2", 0f)
                        )
                ))
                .add(Arguments.of(
                        key("key").eq(Double.MAX_VALUE),
                        asList(
                                new Metadata().put("key", Double.MAX_VALUE),
                                new Metadata().put("key", Double.MAX_VALUE).put("key2", 0d)
                        ),
                        asList(
                                new Metadata().put("key", -Double.MAX_VALUE),
                                new Metadata().put("key", Double.MIN_VALUE),
                                new Metadata().put("key", Math.nextDown(Double.MAX_VALUE)),
                                new Metadata().put("key2", Double.MAX_VALUE)
                        )
                ))


                // === GreaterThan ==

                .add(Arguments.of(
                        "key > 'b'",
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
                        "key > 1",
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
                        "key > 1",
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
                        "key > 1.1",
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
                        "key > 1.1",
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
                        "key >= 'b'",
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
                        "key >= 1",
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
                        "key >= 1",
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
                        "key >= 1.1",
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
                        "key >= 1.1",
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
                        "key < 'b'",
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
                        "key < 1",
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
                        "key < 1",
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
                        "key < 1.1",
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
                        "key < 1.1",
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
                        "key <= 'b'",
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
                        "key <= 1",
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
                        "key <= 1",
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
                        "key <= 1.1",
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
                        "key <= 1.1",
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
                        "name IN ('Klaus')",
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
                        "name IN ('Klaus', 'Alice')",
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
                        "age IN (42)",
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
                        "age IN (42, 18)",
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
                        "age IN (42)",
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
                        "age IN (42, 18)",
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
                        "age IN (42.0)",
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
                        "age IN (42.0, 18.0)",
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
                        "age IN (42.0)",
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
                        "age IN (42.0, 18.0)",
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
                        "name = 'Klaus' OR name = 'Alice'",
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
                        "name = 'Alice' OR name = 'Klaus'",
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
                .add(Arguments.of(
                        "(name = 'Klaus' OR name = 'Alice')",
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

                // Or: multiple keys
                .add(Arguments.of(
                        "name = 'Klaus' OR age = 42",
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
                        "age = 42 OR name = 'Klaus'",
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
                        "name = 'Klaus' OR age = 42 OR city = 'Munich'",
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
                        "(name = 'Klaus' OR age = 42) OR city = 'Munich'",
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
                .add(Arguments.of(
                        "name = 'Klaus' OR (age = 42 OR city = 'Munich')",
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
                        "name = 'Klaus' AND age = 42",
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
                        "age = 42 AND name = 'Klaus'",
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
                        "name = 'Klaus' AND age = 42 AND city = 'Munich'",
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
                        "(name = 'Klaus' AND age = 42) AND city = 'Munich'",
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
                .add(Arguments.of(
                        "name = 'Klaus' AND (age = 42 AND city = 'Munich')",
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
                        "name = 'Klaus' AND (age = 42 OR city = 'Munich')",
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
                        "(name = 'Klaus' OR age = 42) AND city = 'Munich'",
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
                        "name = 'Klaus' OR age = 42 AND city = 'Munich'",
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
                        "name = 'Klaus' AND age = 42 OR city = 'Munich'",
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
    void should_filter_by_metadata_not(String sqlWhereExpression,
                                       List<Metadata> matchingMetadatas,
                                       List<Metadata> notMatchingMetadatas) {

        MetadataFilter metadataFilter = parser.parse(sqlWhereExpression);

        for (Metadata matchingMetadata : matchingMetadatas) {
            assertThat(metadataFilter.test(matchingMetadata)).isTrue();
        }

        for (Metadata notMatchingMetadata : notMatchingMetadatas) {
            assertThat(metadataFilter.test(notMatchingMetadata)).isFalse();
        }

        Metadata emptyMetadata = new Metadata();
        assertThat(metadataFilter.test(emptyMetadata)).isTrue();
    }

    static Stream<Arguments> should_filter_by_metadata_not() {
        return Stream.<Arguments>builder()

                // === Not ===
                .add(Arguments.of(
                        "NOT(name = 'Klaus')",
                        asList(
                                new Metadata().put("name", "Alice"),
                                new Metadata().put("age", 42)
                        ),
                        asList(
                                new Metadata().put("name", "Klaus"),
                                new Metadata().put("name", "Klaus").put("age", 42)
                        )
                )).add(Arguments.of(
                        "NOT name = 'Klaus'",
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
                        "key != 'a'",
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
                        "key != 1",
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
                        "key != 1",
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
                        "key != 1.1",
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
                        "key != 1.1",
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
                        "name NOT IN ('Klaus')",
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
                        "name NOT IN ('Klaus', 'Alice')",
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
                        "age NOT IN (42)",
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
                        "age NOT IN (42, 18)",
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
                        "age NOT IN (42)",
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
                        "age NOT IN (42, 18)",
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
                        "age NOT IN (42.0)",
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
                        "age NOT IN (42.0, 18.0)",
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
                        "age NOT IN (42.0)",
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
                        "age NOT IN (42.0, 18.0)",
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