package dev.langchain4j.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.retriever.EmbeddingStoreRetriever;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiServicesWithRagIT {

    private static final String ALLOWED_CANCELLATION_PERIOD_DAYS = "61";
    private static final String MIN_BOOKING_PERIOD_DAYS = "17";

    EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeEach
    void beforeEach() {
        ingest(embeddingStore, embeddingModel);
    }

    interface Assistant {

        String answer(String query);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_content_retriever(ChatLanguageModel model) {

        // given
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(1)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .contentRetriever(contentRetriever)
                .build();

        // when
        String answer = assistant.answer("Can I cancel my booking?");

        // then
        assertThat(answer).containsAnyOf(ALLOWED_CANCELLATION_PERIOD_DAYS, MIN_BOOKING_PERIOD_DAYS);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_content_retriever_and_chat_memory(ChatLanguageModel model) {

        // given
        ContentRetriever contentRetriever = spy(EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(1)
                .build());

        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        UserMessage userMessage = UserMessage.from("Hello");
        chatMemory.add(userMessage);
        AiMessage aiMessage = AiMessage.from("Hi, how can I help you today?");
        chatMemory.add(aiMessage);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .contentRetriever(contentRetriever)
                .chatMemory(chatMemory)
                .build();

        String query = "Can I cancel my booking?";

        // when
        String answer = assistant.answer(query);

        // then
        assertThat(answer).containsAnyOf(ALLOWED_CANCELLATION_PERIOD_DAYS, MIN_BOOKING_PERIOD_DAYS);

        verify(contentRetriever).retrieve(Query.from(
                query,
                Metadata.from(
                        UserMessage.from(query),
                        "default",
                        asList(userMessage, aiMessage)
                )
        ));
        verifyNoMoreInteractions(contentRetriever);
    }

    interface MultiUserAssistant {

        String answer(@MemoryId int memoryId, @dev.langchain4j.service.UserMessage String query);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_content_retriever_and_chat_memory_provider(ChatLanguageModel model) {

        // given
        ContentRetriever contentRetriever = spy(EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(1)
                .build());

        MultiUserAssistant assistant = AiServices.builder(MultiUserAssistant.class)
                .chatLanguageModel(model)
                .contentRetriever(contentRetriever)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();

        int memoryId = 1;

        String query = "Can I cancel my booking?";

        // when
        String answer = assistant.answer(memoryId, query);

        // then
        assertThat(answer).containsAnyOf(ALLOWED_CANCELLATION_PERIOD_DAYS, MIN_BOOKING_PERIOD_DAYS);

        verify(contentRetriever).retrieve(Query.from(
                query,
                Metadata.from(UserMessage.from(query), memoryId, emptyList()))
        );
        verifyNoMoreInteractions(contentRetriever);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_query_transformer_and_content_retriever(ChatLanguageModel model) {

        // given
        QueryTransformer queryTransformer = new ExpandingQueryTransformer(model);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(1)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .queryTransformer(queryTransformer)
                        .contentRetriever(contentRetriever)
                        .build())
                .build();

        // when
        String answer = assistant.answer("Can I cancel my booking?");

        // then
        assertThat(answer).containsAnyOf(ALLOWED_CANCELLATION_PERIOD_DAYS, MIN_BOOKING_PERIOD_DAYS);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_query_router_and_content_retriever(ChatLanguageModel model) {

        // given
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(1)
                .build();

        ContentRetriever wrongContentRetriever = (query) -> {
            throw new RuntimeException("Should never be called");
        };

        Map<ContentRetriever, String> retrieverToDescription = new HashMap<>();
        retrieverToDescription.put(contentRetriever, "car rental company terms of use");
        retrieverToDescription.put(wrongContentRetriever, "articles about cats");

        QueryRouter queryRouter = new LanguageModelQueryRouter(model, retrieverToDescription);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .queryRouter(queryRouter)
                        .build())
                .build();

        // when
        String answer = assistant.answer("Can I cancel my booking?");

        // then
        assertThat(answer).containsAnyOf(ALLOWED_CANCELLATION_PERIOD_DAYS, MIN_BOOKING_PERIOD_DAYS);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_content_retriever_and_content_aggregator(ChatLanguageModel model) {

        // given
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .build();

        ScoringModel scoringModel = mock(ScoringModel.class);
        when(scoringModel.scoreAll(any(), any())).thenReturn(Response.from(asList(0.9, 0.7)));
        ContentAggregator contentAggregator = new ReRankingContentAggregator(scoringModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .contentRetriever(contentRetriever)
                        .contentAggregator(contentAggregator)
                        .build())
                .build();

        // when
        String answer = assistant.answer("Can I cancel my booking?");

        // then
        assertThat(answer).containsAnyOf(ALLOWED_CANCELLATION_PERIOD_DAYS, MIN_BOOKING_PERIOD_DAYS);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_all_rag_components(ChatLanguageModel model) {

        // given
        QueryTransformer queryTransformer = new ExpandingQueryTransformer(model);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .build();
        ContentRetriever wrongContentRetriever = (query) -> {
            throw new RuntimeException("Should never be called");
        };
        Map<ContentRetriever, String> retrieverToDescription = new HashMap<>();
        retrieverToDescription.put(contentRetriever, "car rental company terms of use");
        retrieverToDescription.put(wrongContentRetriever, "articles about unicorns");
        QueryRouter queryRouter = new LanguageModelQueryRouter(model, retrieverToDescription);

        ScoringModel scoringModel = mock(ScoringModel.class);
        when(scoringModel.scoreAll(any(), any())).thenReturn(Response.from(asList(0.9, 0.7)));
        ContentAggregator contentAggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .querySelector((queryToContents) -> queryToContents.keySet().iterator().next())
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .queryTransformer(queryTransformer)
                        .queryRouter(queryRouter)
                        .contentAggregator(contentAggregator)
                        .build())
                .build();

        // when
        String answer = assistant.answer("Can I cancel my booking?");

        // then
        assertThat(answer).containsAnyOf(ALLOWED_CANCELLATION_PERIOD_DAYS, MIN_BOOKING_PERIOD_DAYS);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_legacy_retriever(ChatLanguageModel model) {

        // given
        Retriever<TextSegment> legacyRetriever =
                EmbeddingStoreRetriever.from(embeddingStore, embeddingModel, 1);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .retriever(legacyRetriever)
                .build();

        // when
        String answer = assistant.answer("Can I cancel my booking?");

        // then
        assertThat(answer).containsAnyOf(ALLOWED_CANCELLATION_PERIOD_DAYS, MIN_BOOKING_PERIOD_DAYS);
    }

    private void ingest(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        OpenAiTokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);
        DocumentSplitter splitter = DocumentSplitters.recursive(100, 0, tokenizer);
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        Document document = loadDocument(toPath("miles-of-smiles-terms-of-use.txt"), new TextDocumentParser());
        ingestor.ingest(document);
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
                // TODO add more models
        );
    }

    private Path toPath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
