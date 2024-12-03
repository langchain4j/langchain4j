package dev.langchain4j.store.embedding.filter.builder.sql;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class LanguageModelSqlFilterBuilderIT {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";
    private static final int OLLAMA_NUM_PREDICT = 25;

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
        assertThat(filter).isEqualTo(metadataKey("genre").isEqualTo("comedy"));
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_filter_by_genre_and_year(ChatLanguageModel model) {

        // given
        LanguageModelSqlFilterBuilder sqlFilterBuilder = LanguageModelSqlFilterBuilder.builder()
                .chatLanguageModel(model)
                .tableDefinition(table)
                .build();

        Query query = Query.from("I want to watch drama from current year");

        // when
        Filter filter = sqlFilterBuilder.build(query);

        // then
        assertThat(filter).isEqualTo(metadataKey("genre").isEqualTo("drama").and(metadataKey("year").isEqualTo((long) LocalDate.now().getYear())));
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
        assertThat(filter).isIn(
                metadataKey("year").isGreaterThanOrEqualTo(1990L).and(metadataKey("year").isLessThanOrEqualTo(1999L)),
                metadataKey("year").isGreaterThanOrEqualTo(1990L).and(metadataKey("year").isLessThan(2000L))
        );
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
        assertThat(filter).isEqualTo(metadataKey("year").isEqualTo((long) LocalDate.now().getYear() - 1));
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
                )
//                Arguments.of(
//                        OllamaChatModel.builder()
//                                .baseUrl(OLLAMA_BASE_URL)
//                                .modelName("sqlcoder")
//                                .numPredict(OLLAMA_NUM_PREDICT)
//                                .build()
//                ),
//                Arguments.of(
//                        OllamaChatModel.builder()
//                                .baseUrl(OLLAMA_BASE_URL)
//                                .modelName("codellama")
//                                .numPredict(OLLAMA_NUM_PREDICT)
//                                .build()
//                ),
//                Arguments.of(
//                        OllamaChatModel.builder()
//                                .baseUrl(OLLAMA_BASE_URL)
//                                .modelName("mistral")
//                                .numPredict(OLLAMA_NUM_PREDICT)
//                                .build()
//                ),
//                Arguments.of(
//                        OllamaChatModel.builder()
//                                .baseUrl(OLLAMA_BASE_URL)
//                                .modelName("llama2")
//                                .numPredict(OLLAMA_NUM_PREDICT)
//                                .build()
//                ),
//                Arguments.of(
//                        OllamaChatModel.builder()
//                                .baseUrl(OLLAMA_BASE_URL)
//                                .modelName("phi")
//                                .numPredict(OLLAMA_NUM_PREDICT)
//                                .build()
//                )
        );
    }
}