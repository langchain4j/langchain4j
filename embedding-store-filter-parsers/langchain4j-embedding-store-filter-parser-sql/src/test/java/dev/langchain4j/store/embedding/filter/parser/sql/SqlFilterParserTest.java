package dev.langchain4j.store.embedding.filter.parser.sql;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.FilterParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.store.embedding.filter.Filter.*;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.of;

class SqlFilterParserTest {

    Clock clock = Clock.fixed(Instant.now(), UTC);
    FilterParser parser = new SqlFilterParser(clock);

    @ParameterizedTest
    @MethodSource
    void should_parse(String sqlWhereExpression, Filter expectedFilter) {

        // when
        Filter filter = parser.parse(sqlWhereExpression);

        // then
        assertThat(filter).isEqualTo(expectedFilter);
    }

    static Stream<Arguments> should_parse() {
        return Stream.<Arguments>builder()

                // eq
                .add(of(
                        "name = 'Klaus'",
                        metadataKey("name").isEqualTo("Klaus")
                ))
                .add(of(
                        "age = 18",
                        metadataKey("age").isEqualTo(18L)
                ))
                .add(of(
                        "weight = 67.8",
                        metadataKey("weight").isEqualTo(67.8d)
                ))


                // ne
                .add(of(
                        "name != 'Klaus'",
                        metadataKey("name").isNotEqualTo("Klaus")
                ))
                .add(of(
                        "age != 18",
                        metadataKey("age").isNotEqualTo(18L)
                ))
                .add(of(
                        "weight != 67.8",
                        metadataKey("weight").isNotEqualTo(67.8d)
                ))


                // gt
                .add(of(
                        "name > 'Klaus'",
                        metadataKey("name").isGreaterThan("Klaus")
                ))
                .add(of(
                        "age > 18",
                        metadataKey("age").isGreaterThan(18L)
                ))
                .add(of(
                        "weight > 67.8",
                        metadataKey("weight").isGreaterThan(67.8d)
                ))


                // gte
                .add(of(
                        "name >= 'Klaus'",
                        metadataKey("name").isGreaterThanOrEqualTo("Klaus")
                ))
                .add(of(
                        "age >= 18",
                        metadataKey("age").isGreaterThanOrEqualTo(18L)
                ))
                .add(of(
                        "weight >= 67.8",
                        metadataKey("weight").isGreaterThanOrEqualTo(67.8d)
                ))


                // lt
                .add(of(
                        "name < 'Klaus'",
                        metadataKey("name").isLessThan("Klaus")
                ))
                .add(of(
                        "age < 18",
                        metadataKey("age").isLessThan(18L)
                ))
                .add(of(
                        "weight < 67.8",
                        metadataKey("weight").isLessThan(67.8d)
                ))


                // lte
                .add(of(
                        "name <= 'Klaus'",
                        metadataKey("name").isLessThanOrEqualTo("Klaus")
                ))
                .add(of(
                        "age <= 18",
                        metadataKey("age").isLessThanOrEqualTo(18L)
                ))
                .add(of(
                        "weight <= 67.8",
                        metadataKey("weight").isLessThanOrEqualTo(67.8d)
                ))


                // in
                .add(of(
                        "name IN ('Klaus', 'Francine')",
                        metadataKey("name").isIn("Klaus", "Francine")
                ))
                .add(of(
                        "age IN (18, 42)",
                        metadataKey("age").isIn(18L, 42L)
                ))
                .add(of(
                        "weight IN (67.8, 78.9)",
                        metadataKey("weight").isIn(67.8d, 78.9d)
                ))


                // nin
                .add(of(
                        "name NOT IN ('Klaus', 'Francine')",
                        metadataKey("name").isNotIn("Klaus", "Francine")
                ))
                .add(of(
                        "age NOT IN (18, 42)",
                        metadataKey("age").isNotIn(18L, 42L)
                ))
                .add(of(
                        "weight NOT IN (67.8, 78.9)",
                        metadataKey("weight").isNotIn(67.8d, 78.9d)
                ))


                // and
                .add(of(
                        "name = 'Klaus' AND age = 18",
                        and(
                                metadataKey("name").isEqualTo("Klaus"),
                                metadataKey("age").isEqualTo(18L)
                        )
                ))


                // not
                .add(of(
                        "NOT name = 'Klaus'",
                        not(metadataKey("name").isEqualTo("Klaus"))
                ))
                .add(of(
                        "NOT (name = 'Klaus')",
                        not(metadataKey("name").isEqualTo("Klaus"))
                ))


                // or
                .add(of(
                        "name = 'Klaus' OR age = 18",
                        or(
                                metadataKey("name").isEqualTo("Klaus"),
                                metadataKey("age").isEqualTo(18L)
                        )
                ))


                // or x2
                .add(of(
                        "color = 'white' OR color = 'black' OR color = 'red'",
                        or(
                                or(
                                        metadataKey("color").isEqualTo("white"),
                                        metadataKey("color").isEqualTo("black")
                                ),
                                metadataKey("color").isEqualTo("red")
                        )
                ))
                .add(of(
                        "(color = 'white' OR color = 'black') OR color = 'red'",
                        or(
                                or(
                                        metadataKey("color").isEqualTo("white"),
                                        metadataKey("color").isEqualTo("black")
                                ),
                                metadataKey("color").isEqualTo("red")
                        )
                ))
                .add(of(
                        "color = 'white' OR (color = 'black' OR color = 'red')",
                        or(
                                metadataKey("color").isEqualTo("white"),
                                or(
                                        metadataKey("color").isEqualTo("black"),
                                        metadataKey("color").isEqualTo("red")
                                )
                        )
                ))


                // or + and
                .add(of(
                        "color = 'white' OR color = 'black' AND form = 'circle'",
                        or(
                                metadataKey("color").isEqualTo("white"),
                                and(
                                        metadataKey("color").isEqualTo("black"),
                                        metadataKey("form").isEqualTo("circle")
                                )
                        )
                ))
                .add(of(
                        "(color = 'white' OR color = 'black') AND form = 'circle'",
                        and(
                                or(
                                        metadataKey("color").isEqualTo("white"),
                                        metadataKey("color").isEqualTo("black")
                                ),
                                metadataKey("form").isEqualTo("circle")
                        )
                ))


                // and + or
                .add(of(
                        "color = 'white' AND shape = 'circle' OR color = 'red'",
                        or(
                                and(
                                        metadataKey("color").isEqualTo("white"),
                                        metadataKey("shape").isEqualTo("circle")
                                ),
                                metadataKey("color").isEqualTo("red")
                        )
                ))
                .add(of(
                        "color = 'white' AND (shape = 'circle' OR color = 'red')",
                        and(
                                metadataKey("color").isEqualTo("white"),
                                or(
                                        metadataKey("shape").isEqualTo("circle"),
                                        metadataKey("color").isEqualTo("red")
                                )
                        )
                ))


                // and x2
                .add(of(
                        "color = 'white' AND form = 'circle' AND area > 7",
                        and(
                                and(
                                        metadataKey("color").isEqualTo("white"),
                                        metadataKey("form").isEqualTo("circle")
                                ),
                                metadataKey("area").isGreaterThan(7L)
                        )
                ))

                // complete SQL statements
                .add(of(
                        "SELECT * from fake_table WHERE id = 7",
                        metadataKey("id").isEqualTo(7L)
                ))
                .add(of(
                        "select * from fake_table where id = 7",
                        metadataKey("id").isEqualTo(7L)
                ))
                .add(of(
                        "Select * From fake_table Where id = 7",
                        metadataKey("id").isEqualTo(7L)
                ))

                .build();
    }

    @ParameterizedTest
    @MethodSource
    void should_filter_by_metadata(String sqlWhereExpression,
                                   List<Metadata> matchingMetadatas,
                                   List<Metadata> notMatchingMetadatas) {

        Filter filter = parser.parse(sqlWhereExpression);

        for (Metadata matchingMetadata : matchingMetadatas) {
            assertThat(filter.test(matchingMetadata)).isTrue();
        }

        for (Metadata notMatchingMetadata : notMatchingMetadatas) {
            assertThat(filter.test(notMatchingMetadata)).isFalse();
        }

        Metadata emptyMetadata = new Metadata();
        assertThat(filter.test(emptyMetadata)).isFalse();
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
                // TODO does it make sense to test float for equality?

                // double
                //TODO does it make sense to test double for equality?


                // === GreaterThan ===

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

        Filter filter = parser.parse(sqlWhereExpression);

        for (Metadata matchingMetadata : matchingMetadatas) {
            assertThat(filter.test(matchingMetadata)).isTrue();
        }

        for (Metadata notMatchingMetadata : notMatchingMetadatas) {
            assertThat(filter.test(notMatchingMetadata)).isFalse();
        }

        Metadata emptyMetadata = new Metadata();
        assertThat(filter.test(emptyMetadata)).isTrue();
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

    @Test
    void should_support_CURDATE() {

        assertThat(parser.parse("year = YEAR(CURDATE())"))
                .isEqualTo(metadataKey("year").isEqualTo(currentYear()));
        assertThat(parser.parse("month = MONTH(CURDATE())"))
                .isEqualTo(metadataKey("month").isEqualTo(currentMonth()));
    }

    @Test
    void should_support_EXTRACT() {

        assertThat(parser.parse("year = EXTRACT(YEAR FROM CURRENT_DATE)"))
                .isEqualTo(metadataKey("year").isEqualTo(currentYear()));
        assertThat(parser.parse("year = EXTRACT(YEAR FROM CURRENT_TIME)"))
                .isEqualTo(metadataKey("year").isEqualTo(currentYear()));
        assertThat(parser.parse("year = EXTRACT(YEAR FROM CURRENT_TIMESTAMP)"))
                .isEqualTo(metadataKey("year").isEqualTo(currentYear()));

        assertThat(parser.parse("month = EXTRACT(MONTH FROM CURRENT_DATE)"))
                .isEqualTo(metadataKey("month").isEqualTo(currentMonth()));
        assertThat(parser.parse("month = EXTRACT(MONTH FROM CURRENT_TIME)"))
                .isEqualTo(metadataKey("month").isEqualTo(currentMonth()));
        assertThat(parser.parse("month = EXTRACT(MONTH FROM CURRENT_TIMESTAMP)"))
                .isEqualTo(metadataKey("month").isEqualTo(currentMonth()));

        assertThat(parser.parse("week = EXTRACT(WEEK FROM CURRENT_DATE)"))
                .isEqualTo(metadataKey("week").isEqualTo(currentWeek()));
        assertThat(parser.parse("week = EXTRACT(WEEK FROM CURRENT_TIME)"))
                .isEqualTo(metadataKey("week").isEqualTo(currentWeek()));
        assertThat(parser.parse("week = EXTRACT(WEEK FROM CURRENT_TIMESTAMP)"))
                .isEqualTo(metadataKey("week").isEqualTo(currentWeek()));

        assertThat(parser.parse("day = EXTRACT(DAY FROM CURRENT_DATE)"))
                .isEqualTo(metadataKey("day").isEqualTo(currentDayOfMonth()));
        assertThat(parser.parse("day = EXTRACT(DAY FROM CURRENT_TIME)"))
                .isEqualTo(metadataKey("day").isEqualTo(currentDayOfMonth()));
        assertThat(parser.parse("day = EXTRACT(DAY FROM CURRENT_TIMESTAMP)"))
                .isEqualTo(metadataKey("day").isEqualTo(currentDayOfMonth()));

        assertThat(parser.parse("dow = EXTRACT(DOW FROM CURRENT_DATE)"))
                .isEqualTo(metadataKey("dow").isEqualTo(currentDayOfWeek()));
        assertThat(parser.parse("dow = EXTRACT(DOW FROM CURRENT_TIME)"))
                .isEqualTo(metadataKey("dow").isEqualTo(currentDayOfWeek()));
        assertThat(parser.parse("dow = EXTRACT(DOW FROM CURRENT_TIMESTAMP)"))
                .isEqualTo(metadataKey("dow").isEqualTo(currentDayOfWeek()));

        assertThat(parser.parse("doy = EXTRACT(DOY FROM CURRENT_DATE)"))
                .isEqualTo(metadataKey("doy").isEqualTo(currentDayOfYear()));
        assertThat(parser.parse("doy = EXTRACT(DOY FROM CURRENT_TIME)"))
                .isEqualTo(metadataKey("doy").isEqualTo(currentDayOfYear()));
        assertThat(parser.parse("doy = EXTRACT(DOY FROM CURRENT_TIMESTAMP)"))
                .isEqualTo(metadataKey("doy").isEqualTo(currentDayOfYear()));

        assertThat(parser.parse("hour = EXTRACT(HOUR FROM CURRENT_TIME)"))
                .isEqualTo(metadataKey("hour").isEqualTo(currentHour()));
        assertThat(parser.parse("hour = EXTRACT(HOUR FROM CURRENT_TIMESTAMP)"))
                .isEqualTo(metadataKey("hour").isEqualTo(currentHour()));

        assertThat(parser.parse("minute = EXTRACT(MINUTE FROM CURRENT_TIME)"))
                .isEqualTo(metadataKey("minute").isEqualTo(currentMinute()));
        assertThat(parser.parse("minute = EXTRACT(MINUTE FROM CURRENT_TIMESTAMP)"))
                .isEqualTo(metadataKey("minute").isEqualTo(currentMinute()));
    }

    private long currentYear() {
        return LocalDate.now(clock).getYear();
    }

    private long currentMonth() {
        return LocalDate.now(clock).getMonthValue();
    }

    private long currentWeek() {
        return LocalDate.now(clock).get(WEEK_OF_WEEK_BASED_YEAR);
    }

    private long currentDayOfMonth() {
        return LocalDate.now(clock).getDayOfMonth();
    }

    private long currentDayOfWeek() {
        return LocalDate.now(clock).getDayOfWeek().getValue();
    }

    private long currentDayOfYear() {
        return LocalDate.now(clock).getDayOfYear();
    }

    private long currentHour() {
        return LocalTime.now(clock).getHour();
    }

    private long currentMinute() {
        return LocalTime.now(clock).getMinute();
    }

    @Test
    void should_support_arithmetics() {

        assertThat(parser.parse("year = EXTRACT(YEAR FROM CURRENT_DATE) + 1"))
                .isEqualTo(metadataKey("year").isEqualTo(currentYear() + 1));
        assertThat(parser.parse("year = EXTRACT(YEAR FROM CURRENT_DATE) - 1"))
                .isEqualTo(metadataKey("year").isEqualTo(currentYear() - 1));
        assertThat(parser.parse("year = EXTRACT(YEAR FROM CURRENT_DATE) * 2"))
                .isEqualTo(metadataKey("year").isEqualTo(currentYear() * 2));
        assertThat(parser.parse("year = EXTRACT(YEAR FROM CURRENT_DATE) / 2"))
                .isEqualTo(metadataKey("year").isEqualTo(currentYear() / 2));
    }

    @Test
    void should_support_BETWEEN() {

        // given
        String sql = "SELECT name FROM movies WHERE year BETWEEN 1990 AND 1999;";

        // when
        Filter filter = parser.parse(sql);

        // then
        assertThat(filter).isEqualTo(metadataKey("year").isGreaterThanOrEqualTo(1990L).and(metadataKey("year").isLessThanOrEqualTo(1999L)));
    }

    // TODO SELECT * FROM movies WHERE YEAR(year) = 2024 AND genre IN ('comedy', 'drama') ORDER BY RAND() LIMIT 1
}