package dev.langchain4j.store.memory.chat.cassandra;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.Disabled;
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

    @Test
    @Disabled("bug: order of retrieved messages is wrong")
    @EnabledIfEnvironmentVariable(named = "ASTRA_DB_APPLICATION_TOKEN", matches = "Astra.*")
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk.*")
    void chatMemoryAstraTest() {

        // Initialization
        String astraToken = getAstraToken();
        String databaseId = setupDatabase("langchain4j", "langchain4j");

        // Given
        assertNotNull(databaseId);
        assertNotNull(astraToken);

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
}
