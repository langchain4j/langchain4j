package dev.langchain4j.store.embedding.filter.builder.sql;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static dev.langchain4j.data.document.Metadata.metadata;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
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
    void should_filter_by_genre(ChatModel model) {

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
    void should_filter_by_genre_and_year(ChatModel model) {

        // given
        LanguageModelSqlFilterBuilder sqlFilterBuilder = LanguageModelSqlFilterBuilder.builder()
                .chatModel(model)
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
    void should_filter_by_year_range(ChatModel model) {

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
    void should_filter_by_year_using_arithmetics(ChatModel model) {

        // given
        LanguageModelSqlFilterBuilder sqlFilterBuilder = new LanguageModelSqlFilterBuilder(model, table);

        Query query = Query.from("I want to watch some recent movie from the previous year");

        // when
        Filter filter = sqlFilterBuilder.build(query);

        // then
        assertThat(filter).isEqualTo(metadataKey("year").isEqualTo((long) LocalDate.now().getYear() - 1));
    }

    interface Assistant {

        String answer(String query);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_LLM_generated_metadata_filter(ChatModel model) {

        // given
        TextSegment groundhogDay = TextSegment.from("Groundhog Day", new dev.langchain4j.data.document.Metadata().put("genre", "comedy").put("year", 1993));
        TextSegment forrestGump = TextSegment.from("Forrest Gump", metadata("genre", "drama").put("year", 1994));
        TextSegment dieHard = TextSegment.from("Die Hard", metadata("genre", "action").put("year", 1998));

        TableDefinition tableDefinition = TableDefinition.builder()
                .name("movies")
                .addColumn("genre", "VARCHAR", "one of: [comedy, drama, action]")
                .addColumn("year", "INT")
                .build();

        LanguageModelSqlFilterBuilder sqlFilterBuilder = new LanguageModelSqlFilterBuilder(model, tableDefinition);

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        embeddingStore.add(embeddingModel.embed(groundhogDay).content(), groundhogDay);
        embeddingStore.add(embeddingModel.embed(forrestGump).content(), forrestGump);
        embeddingStore.add(embeddingModel.embed(dieHard).content(), dieHard);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .dynamicFilter(sqlFilterBuilder::build)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .contentRetriever(contentRetriever)
                .build();

        // when
        String answer = assistant.answer("Recommend me a good drama from 90s");

        // then
        assertThat(answer).containsIgnoringCase("Gump");
    }

    static Stream<Arguments> models() {
        return Stream.of(
                Arguments.of(
                        OpenAiChatModel.builder()
                                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                                .apiKey(System.getenv("OPENAI_API_KEY"))
                                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                                .modelName(GPT_4_O_MINI)
                                .temperature(0.0)
                                .logRequests(true)
                                .logResponses(true)
                                .build()
                )
        );
    }
}
