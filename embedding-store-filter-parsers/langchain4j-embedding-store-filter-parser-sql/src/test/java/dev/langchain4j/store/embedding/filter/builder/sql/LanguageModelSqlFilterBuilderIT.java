package dev.langchain4j.store.embedding.filter.builder.sql;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static dev.langchain4j.store.embedding.filter.Filter.Key.key;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class LanguageModelSqlFilterBuilderIT {

    TableDefinition table = new TableDefinition(
            "movies",
            "",
            asList(
                    new ColumnDefinition("name", "VARCHAR(50)", ""),
                    new ColumnDefinition("genre", "VARCHAR(50)", "one of: [comedy, drama, action]"),
                    new ColumnDefinition("year", "INTEGER", "")
            )
    );

    @ParameterizedTest
    @MethodSource("models")
    void should_filter_by_genre(ChatLanguageModel model) {

        // given
        LanguageModelSqlFilterBuilder sqlFilterBuilder = new LanguageModelSqlFilterBuilder(model, table);

        Query query = Query.from("I want to watch something funny");

        // when
        Filter filter = sqlFilterBuilder.build(query);

        // then
        assertThat(filter).isEqualTo(key("genre").eq("comedy"));
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_filter_by_genre_and_year(ChatLanguageModel model) {

        // given
        LanguageModelSqlFilterBuilder sqlFilterBuilder = LanguageModelSqlFilterBuilder.builder()
                .chatLanguageModel(model)
                .tableDefinition(table)
                .build();

        Query query = Query.from("I want to watch something exciting from this year");

        // when
        Filter filter = sqlFilterBuilder.build(query);

        // then
        assertThat(filter).isEqualTo(key("genre").eq("action").and(key("year").eq(2024L)));
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_filter_by_year_range(ChatLanguageModel model) {

        // given
        LanguageModelSqlFilterBuilder sqlFilterBuilder = new LanguageModelSqlFilterBuilder(model, table);

        Query query = Query.from("I want to watch some old movie from 90s");

        // when
        Filter filter = sqlFilterBuilder.build(query);

        // then
        assertThat(filter).isEqualTo(key("year").gte(1990L).and(key("year").lte(1999L)));
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_filter_by_year_using_arithmetics(ChatLanguageModel model) {

        // given
        LanguageModelSqlFilterBuilder sqlFilterBuilder = new LanguageModelSqlFilterBuilder(model, table);

        Query query = Query.from("I want to watch some recent movie from the previous year");

        // when
        Filter filter = sqlFilterBuilder.build(query);

        // then
        assertThat(filter).isEqualTo(key("year").eq(2023L));
    }

    static Stream<Arguments> models() {
        return Stream.of(
                Arguments.of(
                        OpenAiChatModel.builder()
                                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                                .apiKey(System.getenv("OPENAI_API_KEY"))
                                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                                .logRequests(true)
                                .logResponses(true)
                                .build()
                ),
                Arguments.of(
                        OllamaChatModel.builder()
                                .baseUrl("http://localhost:11434")
                                .modelName("sqlcoder")
//                                .numPredict(35)
                                .build()
                ),
                Arguments.of(
                        OllamaChatModel.builder()
                                .baseUrl("http://localhost:11434")
                                .modelName("codellama")
//                                .numPredict(35)
                                .build()
                ),
                Arguments.of(
                        OllamaChatModel.builder()
                                .baseUrl("http://localhost:11434")
                                .modelName("mistral")
//                                .numPredict(35)
                                .build()
                ),
                Arguments.of(
                        OllamaChatModel.builder()
                                .baseUrl("http://localhost:11434")
                                .modelName("llama2")
//                                .numPredict(35)
                                .build()
                ),
                Arguments.of(
                        OllamaChatModel.builder()
                                .baseUrl("http://localhost:11434")
                                .modelName("phi")
//                                .numPredict(35)
                                .build()
                )
        );
    }
}