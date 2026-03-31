package dev.langchain4j.store.memory.chat.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

class MongoDbChatMemoryStoreIT {

    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6.0")).withReuse(true);

    static MongoClient mongoClient;
    static ChatMemoryStore store;

    @BeforeAll
    static void setUp() {
        mongoDBContainer.start();
        mongoClient = MongoClients.create(mongoDBContainer.getReplicaSetUrl());

        store = MongoDbChatMemoryStore.builder()
                .mongoClient(mongoClient)
                .databaseName("test_db")
                .collectionName("test_collection")
                .build();
    }

    @AfterAll
    static void tearDown() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @BeforeEach
    void cleanDatabase() {
        mongoClient.getDatabase("test_db").getCollection("test_collection").drop();
    }

    @Test
    void should_store_and_retrieve_messages() {
        // given
        String memoryId = "user-123";
        assertThat(store.getMessages(memoryId)).isEmpty();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from("Hello"));
        messages.add(AiMessage.from("Hi there!"));

        // when
        store.updateMessages(memoryId, messages);

        // then
        List<ChatMessage> retrieved = store.getMessages(memoryId);
        assertThat(retrieved).hasSize(2);
        assertThat(retrieved.get(0)).isInstanceOf(UserMessage.class);
        assertThat(retrieved.get(1)).isInstanceOf(AiMessage.class);
    }

    @Test
    void should_update_existing_messages() {
        // given
        String memoryId = "user-789";
        List<ChatMessage> messages = new ArrayList<>();

        messages.add(UserMessage.from("Hello"));
        store.updateMessages(memoryId, messages);
        assertThat(store.getMessages(memoryId)).hasSize(1);

        // when
        messages.add(AiMessage.from("Hi there!"));
        store.updateMessages(memoryId, messages);

        // then
        List<ChatMessage> retrieved = store.getMessages(memoryId);
        assertThat(retrieved).hasSize(2);
        assertThat(retrieved.get(0)).isInstanceOf(UserMessage.class);
        assertThat(retrieved.get(1)).isInstanceOf(AiMessage.class);
    }

    @Test
    void should_delete_messages() {
        // given
        String memoryId = "user-456";
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from("Delete me"));

        store.updateMessages(memoryId, messages);
        assertThat(store.getMessages(memoryId)).hasSize(1);

        // when
        store.deleteMessages(memoryId);

        // then
        assertThat(store.getMessages(memoryId)).isEmpty();
    }

    @Test
    void should_throw_exception_when_invalid_parameters() {
        // when / then
        assertThatThrownBy(() -> MongoDbChatMemoryStore.builder()
                        .databaseName("test_db")
                        .collectionName("test_collection")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mongoClient cannot be null");

        // when / then
        assertThatThrownBy(() -> MongoDbChatMemoryStore.builder()
                        .mongoClient(mongoClient)
                        .collectionName("test_collection")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("databaseName cannot be null or blank");
    }

    @Test
    void should_create_ttl_index_without_errors() {
        // when
        MongoDbChatMemoryStore storeWithTtl = MongoDbChatMemoryStore.builder()
                .mongoClient(mongoClient)
                .databaseName("test_db")
                .collectionName("test_collection_ttl")
                .expireAfterSeconds(3600L)
                .build();

        // then
        boolean indexExists = false;
        for (Document index : mongoClient
                .getDatabase("test_db")
                .getCollection("test_collection_ttl")
                .listIndexes()) {
            if ("updatedAt_1".equals(index.getString("name"))) {
                indexExists = true;
                break;
            }
        }
        assertThat(indexExists).isTrue();
    }

    @Test
    void should_isolate_messages_by_memory_id() {
        // given
        String memoryId1 = "user-1";
        String memoryId2 = "user-2";

        List<ChatMessage> messages1 = List.of(UserMessage.from("Hello from user 1"));
        List<ChatMessage> messages2 = List.of(UserMessage.from("Hello from user 2"));

        // when
        store.updateMessages(memoryId1, messages1);
        store.updateMessages(memoryId2, messages2);

        // then
        assertThat(store.getMessages(memoryId1)).containsExactly(UserMessage.from("Hello from user 1"));
        assertThat(store.getMessages(memoryId2)).containsExactly(UserMessage.from("Hello from user 2"));
    }
}
