package dev.langchain4j.store.memory.chat.mongodb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.Document;

/**
 * Implementation of {@link ChatMemoryStore} backed by MongoDB.
 * <p>
 * It persists chat messages as serialized JSON within a MongoDB collection, and
 * supports automatic message expiration via a MongoDB TTL index.
 */
public class MongoDbChatMemoryStore implements ChatMemoryStore {

    private final MongoCollection<Document> collection;

    /**
     * Constructor for MongoDbChatMemoryStore.
     *
     * @param mongoClient        The MongoDB client instance.
     * @param databaseName       The name of the MongoDB database.
     * @param collectionName     The name of the MongoDB collection to store chat memory.
     * @param expireAfterSeconds The TTL in seconds for the chat memory. If {@code null} or {@code <= 0}, no TTL is applied.
     */
    public MongoDbChatMemoryStore(
            MongoClient mongoClient, String databaseName, String collectionName, Long expireAfterSeconds) {
        ensureNotNull(mongoClient, "mongoClient");
        ensureNotBlank(databaseName, "databaseName");
        ensureNotBlank(collectionName, "collectionName");

        this.collection = mongoClient.getDatabase(databaseName).getCollection(collectionName);

        if (expireAfterSeconds != null && expireAfterSeconds > 0) {
            IndexOptions indexOptions = new IndexOptions().expireAfter(expireAfterSeconds, TimeUnit.SECONDS);
            try {
                this.collection.createIndex(Indexes.ascending("updatedAt"), indexOptions);
            } catch (MongoCommandException e) {
                if (e.getErrorCode() == 85) {
                    throw new RuntimeException(
                            "An index on 'updatedAt' already exists with different options. "
                                    + "Drop the existing index manually in MongoDB to apply the new expireAfterSeconds value.",
                            e);
                } else {
                    throw new RuntimeException("Failed to create TTL index for ChatMemoryStore", e);
                }
            }
        }
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Document doc = collection.find(Filters.eq("_id", memoryId.toString())).first();
        if (doc == null || !doc.containsKey("messages")) {
            return new ArrayList<>();
        }
        String json = doc.getString("messages");
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = ChatMessageSerializer.messagesToJson(messages);
        Document doc = new Document("_id", memoryId.toString())
                .append("messages", json)
                .append("updatedAt", new Date());

        collection.replaceOne(Filters.eq("_id", memoryId.toString()), doc, new ReplaceOptions().upsert(true));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        collection.deleteOne(Filters.eq("_id", memoryId.toString()));
    }

    /**
     * Creates a new builder for {@link MongoDbChatMemoryStore}.
     *
     * @return A new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link MongoDbChatMemoryStore}.
     */
    public static class Builder {
        private MongoClient mongoClient;
        private String databaseName;
        private String collectionName = "chat_memory";
        private Long expireAfterSeconds;

        /**
         * Sets the MongoDB client.
         *
         * @param mongoClient The MongoDB client.
         * @return This builder.
         */
        public Builder mongoClient(MongoClient mongoClient) {
            this.mongoClient = mongoClient;
            return this;
        }

        /**
         * Sets the database name.
         *
         * @param databaseName The database name.
         * @return This builder.
         */
        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        /**
         * Sets the collection name. Defaults to "chat_memory".
         *
         * @param collectionName The collection name.
         * @return This builder.
         */
        public Builder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        /**
         * Sets the expiration time for chat memory documents.
         *
         * @param expireAfterSeconds The number of seconds after which documents expire.
         * @return This builder.
         */
        public Builder expireAfterSeconds(Long expireAfterSeconds) {
            this.expireAfterSeconds = expireAfterSeconds;
            return this;
        }

        /**
         * Builds the {@link MongoDbChatMemoryStore}.
         *
         * @return A configured instance of {@link MongoDbChatMemoryStore}.
         */
        public MongoDbChatMemoryStore build() {
            return new MongoDbChatMemoryStore(mongoClient, databaseName, collectionName, expireAfterSeconds);
        }
    }
}
