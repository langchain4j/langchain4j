package dev.langchain4j.store.memory.chat.oracle;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * Oracle Database implementation of {@link ChatMemoryStore}.
 *
 * <p>Messages are stored as a JSON array in a single row identified by memory id.
 */
public final class OracleChatMemoryStore implements ChatMemoryStore {

    private static final String DEFAULT_TABLE_NAME = "CHAT_MEMORY";
    private static final String DEFAULT_MEMORY_ID_COLUMN_NAME = "MEMORY_ID";
    private static final String DEFAULT_CONTENT_COLUMN_NAME = "CONTENT";
    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern QUOTED_IDENTIFIER = Pattern.compile("^\"([^\"]|\"\")+\"$");

    private final DataSource dataSource;
    private final String selectSql;
    private final String mergeSql;
    private final String deleteSql;

    private OracleChatMemoryStore(Builder builder) {
        if (builder.dataSource == null) {
            throw new IllegalArgumentException("dataSource cannot be null");
        }

        this.dataSource = builder.dataSource;

        String tableName =
                builder.tableName == null ? DEFAULT_TABLE_NAME : validateIdentifier(builder.tableName, "tableName");
        String memoryIdColumnName = builder.memoryIdColumnName == null
                ? DEFAULT_MEMORY_ID_COLUMN_NAME
                : validateIdentifier(builder.memoryIdColumnName, "memoryIdColumnName");
        String contentColumnName = builder.contentColumnName == null
                ? DEFAULT_CONTENT_COLUMN_NAME
                : validateIdentifier(builder.contentColumnName, "contentColumnName");

        this.selectSql = "SELECT " + contentColumnName + " FROM " + tableName + " WHERE " + memoryIdColumnName + " = ?";
        this.mergeSql = "MERGE INTO " + tableName + " t "
                + "USING (SELECT ? AS " + memoryIdColumnName + ", ? AS " + contentColumnName + " FROM dual) s "
                + "ON (t." + memoryIdColumnName + " = s." + memoryIdColumnName + ") "
                + "WHEN MATCHED THEN UPDATE SET t." + contentColumnName + " = s." + contentColumnName + " "
                + "WHEN NOT MATCHED THEN INSERT (" + memoryIdColumnName + ", " + contentColumnName + ") "
                + "VALUES (s." + memoryIdColumnName + ", s." + contentColumnName + ")";
        this.deleteSql = "DELETE FROM " + tableName + " WHERE " + memoryIdColumnName + " = ?";
    }

    private static String validateIdentifier(String identifier, String fieldName) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        if (SIMPLE_IDENTIFIER.matcher(identifier).matches()) {
            return identifier;
        }
        if (QUOTED_IDENTIFIER.matcher(identifier).matches()) {
            return identifier;
        }
        throw new IllegalArgumentException(fieldName + " contains unsupported characters: " + identifier);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        if (memoryId == null) {
            throw new IllegalArgumentException("memoryId cannot be null");
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(selectSql)) {

            statement.setObject(1, memoryId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Collections.emptyList();
                }

                String json = resultSet.getString(1);
                if (json == null || json.isEmpty()) {
                    return Collections.emptyList();
                }
                return ChatMessageDeserializer.messagesFromJson(json);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get messages for memoryId=" + memoryId, e);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (memoryId == null) {
            throw new IllegalArgumentException("memoryId cannot be null");
        }
        if (messages == null) {
            throw new IllegalArgumentException("messages cannot be null");
        }

        String json = ChatMessageSerializer.messagesToJson(messages);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(mergeSql)) {

            statement.setObject(1, memoryId);
            statement.setString(2, json);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update messages for memoryId=" + memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        if (memoryId == null) {
            throw new IllegalArgumentException("memoryId cannot be null");
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(deleteSql)) {

            statement.setObject(1, memoryId);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete messages for memoryId=" + memoryId, e);
        }
    }

    public static final class Builder {
        private DataSource dataSource;
        private String tableName;
        private String memoryIdColumnName;
        private String contentColumnName;

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder memoryIdColumnName(String memoryIdColumnName) {
            this.memoryIdColumnName = memoryIdColumnName;
            return this;
        }

        public Builder contentColumnName(String contentColumnName) {
            this.contentColumnName = contentColumnName;
            return this;
        }

        public OracleChatMemoryStore build() {
            return new OracleChatMemoryStore(this);
        }
    }
}
