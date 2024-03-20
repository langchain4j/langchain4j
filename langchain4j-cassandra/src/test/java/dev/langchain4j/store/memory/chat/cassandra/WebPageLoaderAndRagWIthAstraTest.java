package dev.langchain4j.store.memory.chat.cassandra;

import com.dtsx.astra.sdk.AstraDBAdmin;
import com.dtsx.astra.sdk.cassio.CassandraSimilarityMetric;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.loader.UrlDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.source.UrlSource;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.document.transformer.HtmlTextExtractor;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.cassandra.CassandraEmbeddingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.dtsx.astra.sdk.utils.TestUtils.TEST_REGION;
import static com.dtsx.astra.sdk.utils.TestUtils.getAstraToken;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_EMBEDDING_ADA_002;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

public class WebPageLoaderAndRagWIthAstraTest {

    public static final String DB_NAME = "langchain4j";

    
    @Test
    @EnabledIfEnvironmentVariable(named = "ASTRA_DB_APPLICATION_TOKEN", matches = "Astra.*")
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk.*")
    void shouldRagWithOpenAiAndAstra() throws IOException {

        // Database Id
        UUID databaseId = new AstraDBAdmin(getAstraToken()).createDatabase(DB_NAME);
        assertThat(databaseId).isNotNull();

        // OpenAI Key
        String openAIKey = System.getenv("OPENAI_API_KEY");
        assertThat(openAIKey).isNotNull();

        // --- Documents Ingestion ---

        // Parsing input file
        //Path path = new File(getClass().getResource("/story-about-happy-carrot.txt").getFile()).toPath();
        //Document document = FileSystemDocumentLoader.loadDocument(path, new TextDocumentParser());

        //Document document = UrlDocumentLoader.load("https://beta.goodbards.ai", new HtmlDocumentParser());;

        HtmlTextExtractor transformer = new HtmlTextExtractor();

        UrlSource.from("https://beta.goodbards.ai").inputStream();

        Document htmlDocument = Document.from("https://beta.goodbards.ai");
        Document goodbardsBetaHomePage = transformer.transform(htmlDocument);

        System.out.println(goodbardsBetaHomePage.text());

        DocumentSplitter splitter = DocumentSplitters
                .recursive(100, 10, new OpenAiTokenizer(GPT_3_5_TURBO));

        // Embedding model (OpenAI)
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(openAIKey)
                .modelName(TEXT_EMBEDDING_ADA_002)
                .build();

        // Embed the document and it in the store
        CassandraEmbeddingStore embeddingStore = CassandraEmbeddingStore.builderAstra()
                .token(getAstraToken())
                .databaseId(databaseId)
                .databaseRegion(TEST_REGION)
                .keyspace("default_keyspace")
                .table( "goodbards")
                .dimension(1536) // openai model
                .metric(CassandraSimilarityMetric.COSINE)
                .build();
        embeddingStore.clear();

        // Ingest method
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        ingestor.ingest(goodbardsBetaHomePage);

        // --------- RAG -------------

        // Specify the question you want to ask the model
        String question = "What is goodbards ?";

        // Embed the question
        Response<Embedding> questionEmbedding = embeddingModel.embed(question);

        // Find relevant embeddings in embedding store by semantic similarity
        // You can play with parameters below to find a sweet spot for your specific use case
        int maxResults = 3;
        double minScore = 0.8;
        List<EmbeddingMatch<TextSegment>> relevantEmbeddings =
                embeddingStore.findRelevant(questionEmbedding.content(), maxResults, minScore);

        // --------- Chat Template -------------

        // Create a prompt for the model that includes question and relevant embeddings
        PromptTemplate promptTemplate = PromptTemplate.from(
                "Answer the following question to the best of your ability:\n"
                        + "\n"
                        + "Question:\n"
                        + "{{question}}\n"
                        + "\n"
                        + "Base your answer on the following information:\n"
                        + "{{information}}\n"
                        + "Put each sentence on a different line:\n"
        );

        String information = relevantEmbeddings.stream()
                .map(match -> match.embedded().text())
                .collect(joining("\n\n"));

        Map<String, Object> variables = new HashMap<>();
        variables.put("question", question);
        variables.put("information", information);

        Prompt prompt = promptTemplate.apply(variables);

        // Send the prompt to the OpenAI chat model
        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .apiKey(openAIKey)
                .modelName(GPT_3_5_TURBO)
                .temperature(0.7)
                .timeout(ofSeconds(15))
                .maxRetries(3)
                .logResponses(true)
                .logRequests(true)
                .build();

        Response<AiMessage> aiMessage = chatModel.generate(prompt.toUserMessage());

        // See an answer from the model
        String answer = aiMessage.content().text();
        System.out.println(answer);
    }


}
