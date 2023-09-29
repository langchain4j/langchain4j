package dev.langchain4j.store.memory.chat.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.dtsx.astra.sdk.cassio.ClusteredCassandraTable;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.dtsx.astra.sdk.cassio.ClusteredCassandraTable.Record;
import static java.util.stream.Collectors.toList;

/**
 * Implementation of {@link ChatMemoryStore} using Astra DB Vector Search.
 * Table contains all chats. (default name is message_store). Each chat with multiple messages
 * is a partition.Message id is a time uuid.
 *
 * @see <a href="https://docs.datastax.com/en/astra-serverless/docs/vector-search/overview.html">Astra Vector Store Documentation</a>
 */
@Slf4j
public class CassandraChatMemoryStore implements ChatMemoryStore {

    /**
     * Default message store.
     */
    public static final String DEFAULT_TABLE_NAME = "message_store";

    /**
     * Message Table.
     */
    private final ClusteredCassandraTable messageTable;

    /**
     * Constructor for message store
     *
     * @param session      cassandra session
     * @param keyspaceName keyspace name
     * @param tableName    table name
     */
    public CassandraChatMemoryStore(CqlSession session, String keyspaceName, String tableName) {
        messageTable = new ClusteredCassandraTable(session, keyspaceName, tableName);
    }

    /**
     * Constructor for message store
     *
     * @param session      cassandra session
     * @param keyspaceName keyspace name
     */
    public CassandraChatMemoryStore(CqlSession session, String keyspaceName) {
        messageTable = new ClusteredCassandraTable(session, keyspaceName, DEFAULT_TABLE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ChatMessage> getMessages(@NonNull Object memoryId) {
        /*
         * RATIONAL:
         * In the cassandra table the order is explicitly put to DESC with
         * latest to come first (for long conversation for instance). Here we ask
         * for the full history. Instead of changing the multi purpose table
         * we reverse the list.
         */
        List<ChatMessage> latestFirstList = messageTable
                .findPartition(getMemoryId(memoryId))
                .stream()
                .map(this::toChatMessage)
                .collect(toList());
        Collections.reverse(latestFirstList);
        return latestFirstList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateMessages(@NonNull Object memoryId, @NonNull List<ChatMessage> messages) {
        deleteMessages(memoryId);
        messageTable.upsertPartition(messages.stream()
                .map(record -> fromChatMessage(getMemoryId(memoryId), record))
                .collect(toList()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteMessages(@NonNull Object memoryId) {
        messageTable.deletePartition(getMemoryId(memoryId));
    }

    /**
     * Unmarshalling Cassandra row as a Message with proper sub-type.
     *
     * @param record cassandra record
     * @return chat message
     */
    private ChatMessage toChatMessage(@NonNull Record record) {
        try {
            return ChatMessageDeserializer.messageFromJson(record.getBody());
        } catch (Exception e) {
            log.error("Unable to parse message body", e);
            throw new IllegalArgumentException("Unable to parse message body");
        }
    }

    /**
     * Serialize the {@link ChatMessage} as a Cassandra Row.
     *
     * @param memoryId    chat session identifier
     * @param chatMessage chat message
     * @return cassandra row.
     */
    private Record fromChatMessage(@NonNull String memoryId, @NonNull ChatMessage chatMessage) {
        try {
            Record record = new Record();
            record.setRowId(Uuids.timeBased());
            record.setPartitionId(memoryId);
            record.setBody(ChatMessageSerializer.messageToJson(chatMessage));
            return record;
        } catch (Exception e) {
            log.error("Unable to parse message body", e);
            throw new IllegalArgumentException("Unable to parse message body", e);
        }
    }

    private String getMemoryId(Object memoryId) {
        if (!(memoryId instanceof String)) {
            throw new IllegalArgumentException("memoryId must be a String");
        }
        return (String) memoryId;
    }
}