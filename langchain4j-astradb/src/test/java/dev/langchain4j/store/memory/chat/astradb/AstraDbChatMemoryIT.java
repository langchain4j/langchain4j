package dev.langchain4j.store.memory.chat.astradb;

import com.datastax.astra.client.DataAPIClient;
import com.datastax.astra.client.Database;
import com.datastax.astra.client.admin.AstraDBAdmin;
import com.datastax.astra.client.admin.AstraDBDatabaseAdmin;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.dtsx.astra.sdk.utils.TestUtils.getAstraToken;
import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled("AstraDB is not available in the CI")
@Slf4j
public class AstraDbChatMemoryIT {

    static final String TEST_DB = "test_langchain4j";
    static UUID dbId;
    static DataAPIClient client;
    static AstraDBAdmin astraDBAdmin;
    static Database db;

    static AstraDBChatMemoryStore chatMemoryStore;

    @BeforeAll
    static void initStoreForTests() {

        /*
         * Token Value is retrieved from environment Variable 'ASTRA_DB_APPLICATION_TOKEN', it should
         * have Organization Administration permissions (to create db)
         */
        client       = new DataAPIClient(getAstraToken());
        astraDBAdmin = client.getAdmin();

        /*
         * Will create a Database in Astra with the name 'test_langchain4j' if does not exist and work
         * with its identifier. The call is blocking and will wait until the database is ready.
         */
        AstraDBDatabaseAdmin databaseAdmin = (AstraDBDatabaseAdmin) astraDBAdmin.createDatabase(TEST_DB);
        dbId = UUID.fromString(databaseAdmin.getDatabaseInformations().getId());
        assertThat(dbId).isNotNull();
        log.info("[init] - Database exists id={}", dbId);

        /*
         * Initialize the client from the database identifier. A database will host multiple collections.
         * A collection stands for an Embedding Store.
         */
        db = databaseAdmin.getDatabase();
        Assertions.assertThat(db).isNotNull();

        chatMemoryStore = new AstraDBChatMemoryStore(db);
        chatMemoryStore.clear();
        log.info("[init] - Embedding Store initialized");
    }

    @Test
    public void testInsertChat() {
            // When
            String chatSessionId = "chat-" + UUID.randomUUID();

            ChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .chatMemoryStore(chatMemoryStore)
                    .maxMessages(100)
                    .id(chatSessionId)
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
