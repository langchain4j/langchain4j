package dev.langchain4j.store.embedding.astradb;


import com.datastax.astra.client.DataAPIClient;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

import static com.datastax.astra.client.model.SimilarityMetric.COSINE;
import static com.dtsx.astra.sdk.utils.TestUtils.getAstraToken;
import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_3_5_TURBO;
import static dev.langchain4j.model.openai.OpenAiEmbeddingModelName.TEXT_EMBEDDING_ADA_002;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled("AstraDB is not available in the CI")
class AstraDbNaiveRagTestIT {

    static final String VAR_OPENAI_API_KEY = "OPENAI_API_KEY";
    static final String VAR_ASTRA_TOKEN    = "ASTRA_DB_APPLICATION_TOKEN";

    @Test
    @EnabledIfEnvironmentVariable(named = VAR_ASTRA_TOKEN, matches = "Astra.*")
    @EnabledIfEnvironmentVariable(named = VAR_OPENAI_API_KEY, matches = "sk.*")
    void shouldNaiveRagWithOpenAiAndAstraDbTest() {

        // Parsing input file
        Path textFile = new File(Objects.requireNonNull(getClass()
                .getResource("/story-about-happy-carrot.txt"))
                .getFile())
                .toPath();

        // === INGESTION ===

        EmbeddingModel embeddingModel = initEmbeddingModelOpenAi();
        EmbeddingStore<TextSegment> embeddingStore = initEmbeddingStoreAstraDb();
        EmbeddingStoreIngestor.builder()
                .documentSplitter(recursive(100, 10, new OpenAiTokenizer(GPT_3_5_TURBO)))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(loadDocument(textFile, new TextDocumentParser()));

        // === NAIVE RETRIEVER ===

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .minScore(0.5)
                .build();

        Assistant ai = AiServices.builder(Assistant.class)
                .contentRetriever(contentRetriever)
                .chatLanguageModel(initChatLanguageModelOpenAi())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        String response = ai.answer("What vegetable is Happy?");
        assertThat(response).isNotBlank();
        assertThat(response).contains("carrot");
    }

    private ChatLanguageModel initChatLanguageModelOpenAi() {
        return OpenAiChatModel.builder()
                .apiKey(System.getenv(VAR_OPENAI_API_KEY))
                .modelName(GPT_3_5_TURBO)
                .temperature(0.7)
                .timeout(ofSeconds(15))
                .maxRetries(3)
                .logResponses(true)
                .logRequests(true)
                .build();
    }

    private EmbeddingModel initEmbeddingModelOpenAi() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(System.getenv(VAR_OPENAI_API_KEY))
                .modelName(TEXT_EMBEDDING_ADA_002)
                .build();
    }

    private EmbeddingStore<TextSegment> initEmbeddingStoreAstraDb() {
        return new AstraDbEmbeddingStore(
                // Astra Db Client
                new DataAPIClient(getAstraToken())
                    // Access the 'admin' part
                    .getAdmin()
                    // To create a database if it does not exist
                    .createDatabase(AstraDbEmbeddingStoreIT.TEST_DB)
                    // Select the created db
                    .getDatabase()
                    // And create a collection if it does not exist
                    .createCollection("story_collection", 1536, COSINE));
    }

}