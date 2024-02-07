package dev.langchain4j.store.embedding.filter.sql;

import dev.langchain4j.store.embedding.filter.MetadataFilter;
import dev.langchain4j.store.embedding.filter.MetadataFilterParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static dev.langchain4j.store.embedding.filter.MetadataFilter.MetadataKey.key;
import static dev.langchain4j.store.embedding.filter.MetadataFilter.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.of;

class SqlMetadataFilterParserTest {

    MetadataFilterParser parser = new SqlMetadataFilterParser();

    @ParameterizedTest
    @MethodSource
    void should_parse(String sqlWhereExpression, MetadataFilter expectedExpression) {

        // when
        MetadataFilter expression = parser.parse(sqlWhereExpression);

        // then
        assertThat(expression).isEqualTo(expectedExpression);
    }

    static Stream<Arguments> should_parse() {
        return Stream.<Arguments>builder()

                .add(of(
                        "name = 'Klaus'",
                        key("name").eq("Klaus")
                ))
                .add(of(
                        "age = 18",
                        key("age").eq(18)
                ))
                .add(of(
                        "weight = 67.8",
                        key("weight").eq(67.8)
                ))

                .add(of(
                        "name > 'Klaus'",
                        key("name").gt("Klaus")
                ))
                .add(of(
                        "age > 18",
                        key("age").gt(18)
                ))
                .add(of(
                        "weight > 67.8",
                        key("weight").gt(67.8)
                ))


                .add(of(
                        "name < 'Klaus'",
                        key("name").lt("Klaus")
                ))
                .add(of(
                        "age < 18",
                        key("age").lt(18)
                ))
                .add(of(
                        "weight < 67.8",
                        key("weight").lt(67.8)
                ))


                .add(of(
                        "name = 'Klaus' AND age = 18",
                        and(
                                key("name").eq("Klaus"),
                                key("age").eq(18)
                        )
                ))
                .add(of(
                        "name = 'Klaus' OR age = 18",
                        or(
                                key("name").eq("Klaus"),
                                key("age").eq(18)
                        )
                ))
                .add(of(
                        "NOT name = 'Klaus'",
                        not(key("name").eq("Klaus"))
                ))
                .add(of(
                        "NOT (name = 'Klaus')",
                        not(key("name").eq("Klaus"))
                ))

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
                        "color = 'white' AND form = 'circle' AND transparent = false",
                        and(
                                and(
                                        key("color").eq("white"),
                                        key("form").eq("circle")
                                ),
                                key("transparent").eq(false)
                        )
                ))

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
                        "(color = 'white' OR color = 'black') AND form = 'circle'",
                        and(
                                or(
                                        key("color").eq("white"),
                                        key("color").eq("black")
                                ),
                                key("form").eq("circle")
                        )
                ))

                // TODO reverse AND and OR

                // TODO more variations - multiple OR and AND and NOT etc

                .build();
    }
}