package dev.langchain4j.store.memory.chat.cassandra;

import com.datastax.astra.sdk.AstraClient;
import com.dtsx.astra.sdk.utils.TestUtils;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.UUID;

import static com.dtsx.astra.sdk.utils.TestUtils.*;
import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test Cassandra Chat Memory Store with a Saas DB.
 */
public class ChatMemoryStoreAstraTest {

    private static final String TEST_DATABASE = "langchain4j";
    private static final String TEST_KEYSPACE = "langchain4j";

    @Test
    @EnabledIfEnvironmentVariable(named = "ASTRA_DB_APPLICATION_TOKEN", matches = "Astra.*")
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk.*")
    void chatMemoryAstraTest() {

        // Initialization
        String astraToken = getAstraToken();
        String databaseId = setupDatabase(TEST_DATABASE, TEST_KEYSPACE);

        // Given
        assertNotNull(databaseId);
        assertNotNull(astraToken);

        // Flush Table before test
        truncateTable(databaseId, TEST_KEYSPACE, CassandraChatMemoryStore.DEFAULT_TABLE_NAME);

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
        UserMessage userMessage = userMessage("I will ask you a few question about ff4j.");
        chatMemory.add(userMessage);

        AiMessage aiMessage = aiMessage("Sure, go ahead!");
        chatMemory.add(aiMessage);

        // Then
        assertThat(chatMemory.messages()).containsExactly(userMessage, aiMessage);
    }

    private void truncateTable(String databaseId, String keyspace, String table) {
        try (AstraClient astraClient = AstraClient.builder()
                .withToken(getAstraToken())
                .withCqlKeyspace(keyspace)
                .withDatabaseId(databaseId)
                .withDatabaseRegion(TestUtils.TEST_REGION)
                .enableCql()
                .enableDownloadSecureConnectBundle()
                .build()) {
            astraClient.cqlSession()
                    .execute("TRUNCATE TABLE " + table);
        }
    }
}
