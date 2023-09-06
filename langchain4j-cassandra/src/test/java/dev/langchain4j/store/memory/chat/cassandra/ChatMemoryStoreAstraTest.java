package dev.langchain4j.store.memory.chat.cassandra;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.dtsx.astra.sdk.utils.TestUtils.TEST_REGION;
import static com.dtsx.astra.sdk.utils.TestUtils.readToken;
import static com.dtsx.astra.sdk.utils.TestUtils.setupDatabase;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static java.time.Duration.ofSeconds;

/**
 * Test Cassandra Chat Memory Store with a Saas DB.
 */
public class ChatMemoryStoreAstraTest {

    private static String astraToken;
    private static String databaseId;
    private static String openAIKey;

    @BeforeAll
    @Disabled("To run you need Astra keys")
    public static void setupEnvironment() throws InterruptedException {
        astraToken  = readToken();
        databaseId  = setupDatabase("langchain4j", "langchain4j");
        openAIKey   = System.getenv("OPENAI_API_KEY");
    }

    @Test
    @Disabled("To run you need Astra keys")
    public void chatMemoryAstraTest() {
        // Given
        Assertions.assertNotNull(openAIKey);
        Assertions.assertNotNull(databaseId);
        Assertions.assertNotNull(astraToken);
        // Given
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(openAIKey)
                .modelName(GPT_3_5_TURBO)
                .temperature(0.3)
                .timeout(ofSeconds(120))
                .logRequests(true)
                .logResponses(true)
                .build();
        // When
        ChatMemoryStore chatMemoryStore =
                new AstraDbChatMemoryStore(astraToken, databaseId, TEST_REGION, "langchain4j");
        // When
        String chatSessionId = "chat-" + UUID.randomUUID();
        ChatMemory chatMemory = TokenWindowChatMemory.builder()
                .chatMemoryStore(chatMemoryStore)
                .id(chatSessionId)
                .maxTokens(300, new OpenAiTokenizer(GPT_3_5_TURBO))
                .build();
        // When
        chatMemory.add(userMessage("I will ask you a few question about ff4j. Response in a single sentence"));
        chatMemory.add(userMessage("Can I use it with Javascript ? "));
        // Then
        String output = model.sendMessages(chatMemory.messages()).text();
        Assertions.assertNotNull(output);
    }
}
