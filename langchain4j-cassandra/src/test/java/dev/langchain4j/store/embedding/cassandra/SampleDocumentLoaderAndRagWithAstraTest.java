package dev.langchain4j.store.embedding.cassandra;

import com.dtsx.astra.sdk.utils.TestUtils;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
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
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.dtsx.astra.sdk.utils.TestUtils.readToken;
import static com.dtsx.astra.sdk.utils.TestUtils.setupDatabase;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_EMBEDDING_ADA_002;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.joining;

public class SampleDocumentLoaderAndRagWithAstraTest {

    private static String astraToken;
    private static String databaseId;
    private static String openAIKey;

    @BeforeAll
    public static void setupEnvironment() throws InterruptedException {
        astraToken  = readToken();
        databaseId  = setupDatabase("langchain4j", "langchain4j");
        openAIKey   = System.getenv("OPENAI_API_KEY");
    }

    @Test
    @Disabled("To run you need both OpenAi and Astra keys")
    public void shouldRagWithOpenAiAndAstra() {
        // Given
        Assertions.assertNotNull(openAIKey);
        Assertions.assertNotNull(databaseId);
        Assertions.assertNotNull(astraToken);

        // --- Ingesting documents ---

        // Parsing input file
        Document document = FileSystemDocumentLoader
                .loadDocument(Objects.requireNonNull(SampleDocumentLoaderAndRagWithAstraTest.class
                                .getClassLoader()
                                .getResource("story-about-happy-carrot.txt"))
                                .getFile());
        DocumentSplitter splitter = DocumentSplitters
                .recursive(100, new OpenAiTokenizer(GPT_3_5_TURBO));

        // Embedding model (OpenAI)
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(openAIKey)
                .modelName(TEXT_EMBEDDING_ADA_002)
                .timeout(ofSeconds(15))
                .logRequests(true)
                .logResponses(true)
                .build();

        // Embed the document and it in the store
        EmbeddingStore<TextSegment> embeddingStore = AstraDbEmbeddingStore.builder()
                .token(astraToken)
                .database(databaseId, TestUtils.TEST_REGION)
                .table("langchain4j", "table_story")
                .vectorDimension(1536)
                .build();

        // Ingest method 2
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        ingestor.ingest(document);

        // --------- RAG -------------

        // Specify the question you want to ask the model
        String question = "Who is Charlie?";

        // Embed the question
        Embedding questionEmbedding = embeddingModel.embed(question);

        // Find relevant embeddings in embedding store by semantic similarity
        // You can play with parameters below to find a sweet spot for your specific use case
        int maxResults = 3;
        double minScore = 0.8;
        List<EmbeddingMatch<TextSegment>> relevantEmbeddings =
                embeddingStore.findRelevant(questionEmbedding, maxResults, minScore);

        // --------- Chat Template -------------

        // Create a prompt for the model that includes question and relevant embeddings
        PromptTemplate promptTemplate = PromptTemplate.from(
                "Answer the following question to the best of your ability:\n"
                        + "\n"
                        + "Question:\n"
                        + "{{question}}\n"
                        + "\n"
                        + "Base your answer on the following information:\n"
                        + "{{information}}");

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

        AiMessage aiMessage = chatModel.sendUserMessage(prompt.toUserMessage());

        // See an answer from the model
        String answer = aiMessage.text();
        System.out.println(answer);
    }
}