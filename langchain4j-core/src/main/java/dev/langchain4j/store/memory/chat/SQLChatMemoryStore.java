package dev.langchain4j.store.memory.chat;

import static dev.langchain4j.internal.Utils.getOrDefault;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SQLChatMemoryStore implements ChatMemoryStore{

    private static final Logger logger = LoggerFactory.getLogger(SQLChatMemoryStore.class);

    private static final String DEFAULT_TABLE = "chat_memory";
    private static final String DEFAULT_MEMORY_ID_COLUMN = "memory_id";
    private static final String DEFAULT_CONTENT_COLUMN = "content";

    private final DataSource dataSource;
    private final SQLDialect sqlDialect;
    private final String tableName;
    private final String memoryIdColumnName;
    private final String contentColumnName;

    private final String selectQuery;
    private final String updateQuery;
    private final String deleteQuery;

    private SQLChatMemoryStore(SQLChatMemoryStoreBuilder builder) {
        this.dataSource = builder.dataSource;
        this.sqlDialect = builder.sqlDialect;
        this.tableName = getOrDefault(builder.tableName, DEFAULT_TABLE);
        this.memoryIdColumnName = getOrDefault(builder.memoryIdColumnName, DEFAULT_MEMORY_ID_COLUMN);
        this.contentColumnName = getOrDefault(builder.contentColumnName, DEFAULT_CONTENT_COLUMN);

        this.selectQuery = "SELECT " + contentColumnName + " from " + tableName + " where " + memoryIdColumnName + "=?";
        this.updateQuery = sqlDialect.upsertSql(tableName, memoryIdColumnName, contentColumnName);
        this.deleteQuery = "DELETE FROM " + tableName + " WHERE " + memoryIdColumnName + "=?";

        if (!getOrDefault(builder.autoCreateTable, true) && !doesTableAndColumnExist()) {
            throw new RuntimeException("Table or Column with specified names does not exist");
        }
        if (getOrDefault(builder.autoCreateTable, true)) {
            createTable();
        }
    }

    public static SQLChatMemoryStoreBuilder builder() {
        return new SQLChatMemoryStoreBuilder();
    }

    @Override
    public List<ChatMessage> getMessages(final Object memoryId) {

        List<ChatMessage> result = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(selectQuery);) {

            ps.setObject(1, memoryId);
            try (ResultSet resultSet = ps.executeQuery();) {
                if (!resultSet.next()) {
                    return Collections.emptyList();
                }
                result = ChatMessageDeserializer.messagesFromJson(resultSet.getString(1));
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve messages for memoryId=" + memoryId + " " + e);
        }
        return result;
    }

    @Override
    public void updateMessages(final Object memoryId, final List<ChatMessage> messages) {

        String jsonContent = ChatMessageSerializer.messagesToJson(messages);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(updateQuery)) {

            ps.setObject(1, memoryId);
            ps.setString(2, jsonContent);
            try(ResultSet resultSet = ps.executeQuery()) {
                if (!resultSet.next()) {
                    logger.debug("Update Message: No messages updated for memoryId={}", memoryId);
                }
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to update messages for memoryId=" + memoryId + " " + e);
        }
    }

    @Override
    public void deleteMessages(final Object memoryId) {

        try(Connection connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(deleteQuery)) {
            ps.setObject(1, memoryId);
            int row = ps.executeUpdate();
            if (row == 0) {
                logger.debug("Delete Message: No row found with memoryId={}", memoryId);
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to delete messages for memoryId=" + memoryId + " " + e);
        }
    }

    private void createTable() {
        String ddl = sqlDialect.createTableSql(tableName, memoryIdColumnName, contentColumnName);

        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(ddl);
            logger.debug("Table '{}' ensured via dialect={}", tableName, sqlDialect);
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to create table '" + tableName + "'", e);
        }
    }

    private boolean doesTableAndColumnExist() {

        boolean doesTableExist;
        boolean doesColumnsExist = false;

        try(Connection connection = dataSource.getConnection()){
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            doesTableExist = (databaseMetaData.getTables(null, null, tableName, new String[]{"TABLE"})).next();
            if (doesTableExist) {
                doesColumnsExist = (databaseMetaData.getColumns(null, null, tableName, memoryIdColumnName)).next()
                        && (databaseMetaData.getColumns(null, null, tableName, contentColumnName)).next();;

            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return doesTableExist && doesColumnsExist;
    }

    public static class SQLChatMemoryStoreBuilder {

        private DataSource dataSource;
        private SQLDialect sqlDialect;
        private String tableName;
        private Boolean autoCreateTable;
        private String memoryIdColumnName;
        private String contentColumnName;

        public SQLChatMemoryStoreBuilder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public SQLChatMemoryStoreBuilder sqlDialect(SQLDialect sqlDialect) {
            this.sqlDialect = sqlDialect;
            return this;
        }

        public SQLChatMemoryStoreBuilder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public SQLChatMemoryStoreBuilder autoCreateTable(Boolean autoCreateTable) {
            this.autoCreateTable = autoCreateTable;
            return this;
        }

        public SQLChatMemoryStoreBuilder memoryIdColumnName(String memoryIdColumnName) {
            this.memoryIdColumnName = memoryIdColumnName;
            return this;
        }

        public SQLChatMemoryStoreBuilder contentColumnName(String contentColumnName) {
            this.contentColumnName = contentColumnName;
            return this;
        }

        public SQLChatMemoryStore build() {
            return new SQLChatMemoryStore(this);
        }

    }
}
