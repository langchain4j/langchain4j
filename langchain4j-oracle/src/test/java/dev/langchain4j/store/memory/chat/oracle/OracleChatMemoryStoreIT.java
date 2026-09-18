package dev.langchain4j.store.memory.chat.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.loader.OracleContainerTestBase;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OracleChatMemoryStoreIT extends OracleContainerTestBase {

    private static final String MEMORY_ID_COLUMN = "memory_id";
    private static final String CONTENT_COLUMN = "content";

    private String tableName;
    private OracleChatMemoryStore store;

    @BeforeEach
    void setUp() throws SQLException {
        tableName = "LANGCHAIN4J_CHAT_MEMORY_"
                + Long.toString(Math.abs(System.nanoTime()), 36).toUpperCase(Locale.ROOT);
        createTable(tableName, MEMORY_ID_COLUMN, CONTENT_COLUMN);
        store = OracleChatMemoryStore.builder()
                .dataSource(getDataSource())
                .tableName(tableName)
                .build();
    }

    @AfterEach
    void tearDown() throws SQLException {
        dropTableIfExists(tableName);
    }

    @Test
    void should_return_empty_list_for_unknown_memory_id() {
        assertThat(store.getMessages("unknown-id")).isEmpty();
    }

    @Test
    void should_store_and_retrieve_messages() {
        String memoryId = "user-1";
        List<ChatMessage> messages = Arrays.<ChatMessage>asList(
                SystemMessage.from("You are a helpful assistant"),
                UserMessage.from("Hello"),
                AiMessage.from("Hi there"));

        store.updateMessages(memoryId, messages);

        List<ChatMessage> retrieved = store.getMessages(memoryId);
        assertThat(retrieved).hasSize(3);
        assertThat(retrieved.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(retrieved.get(1)).isInstanceOf(UserMessage.class);
        assertThat(retrieved.get(2)).isInstanceOf(AiMessage.class);
    }

    @Test
    void should_replace_messages_on_update() {
        String memoryId = "user-replace";

        store.updateMessages(
                memoryId, Arrays.<ChatMessage>asList(UserMessage.from("Old message"), AiMessage.from("Old reply")));
        store.updateMessages(memoryId, Collections.<ChatMessage>singletonList(UserMessage.from("Brand new message")));

        List<ChatMessage> retrieved = store.getMessages(memoryId);
        assertThat(retrieved).hasSize(1);
        assertThat(((UserMessage) retrieved.get(0)).singleText()).isEqualTo("Brand new message");
    }

    @Test
    void should_delete_messages() {
        String memoryId = "user-delete";
        store.updateMessages(memoryId, Collections.<ChatMessage>singletonList(UserMessage.from("Delete me")));

        store.deleteMessages(memoryId);

        assertThat(store.getMessages(memoryId)).isEmpty();
    }

    @Test
    void should_isolate_messages_by_memory_id() {
        store.updateMessages("a", Collections.<ChatMessage>singletonList(UserMessage.from("Message A")));
        store.updateMessages("b", Collections.<ChatMessage>singletonList(UserMessage.from("Message B")));

        assertThat(store.getMessages("a")).containsExactly(UserMessage.from("Message A"));
        assertThat(store.getMessages("b")).containsExactly(UserMessage.from("Message B"));
    }

    @Test
    void should_use_custom_table_and_column_names() throws SQLException {
        String customTable = "LANGCHAIN4J_CHAT_MEMORY_CUSTOM_"
                + Long.toString(Math.abs(System.nanoTime()), 36).toUpperCase(Locale.ROOT);
        try {
            createTable(customTable, "session_id", "messages_json");

            OracleChatMemoryStore customStore = OracleChatMemoryStore.builder()
                    .dataSource(getDataSource())
                    .tableName(customTable)
                    .memoryIdColumnName("session_id")
                    .contentColumnName("messages_json")
                    .build();

            customStore.updateMessages(
                    "custom-user", Collections.<ChatMessage>singletonList(UserMessage.from("Custom columns work")));
            assertThat(customStore.getMessages("custom-user")).hasSize(1);
        } finally {
            dropTableIfExists(customTable);
        }
    }

    @Test
    void should_throw_when_table_does_not_exist() throws SQLException {
        String missingTable = "LANGCHAIN4J_CHAT_MEMORY_MISSING_"
                + Long.toString(Math.abs(System.nanoTime()), 36).toUpperCase(Locale.ROOT);
        dropTableIfExists(missingTable);

        OracleChatMemoryStore missingTableStore = OracleChatMemoryStore.builder()
                .dataSource(getDataSource())
                .tableName(missingTable)
                .build();

        assertThatThrownBy(() -> missingTableStore.getMessages("missing")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void should_accept_quoted_identifiers() throws SQLException {
        String customTable = "\"LANGCHAIN4J CHAT MEMORY "
                + Long.toString(Math.abs(System.nanoTime()), 36).toUpperCase(Locale.ROOT)
                + "\"";
        try {
            createTable(customTable, "\"session-id\"", "\"messages json\"");

            OracleChatMemoryStore customStore = OracleChatMemoryStore.builder()
                    .dataSource(getDataSource())
                    .tableName(customTable)
                    .memoryIdColumnName("\"session-id\"")
                    .contentColumnName("\"messages json\"")
                    .build();

            customStore.updateMessages(
                    "quoted-user", Collections.<ChatMessage>singletonList(UserMessage.from("quoted works")));
            assertThat(customStore.getMessages("quoted-user")).hasSize(1);
        } finally {
            dropTableIfExists(customTable);
        }
    }

    @Test
    void should_reject_injected_table_name() {
        assertThatThrownBy(() -> OracleChatMemoryStore.builder()
                        .dataSource(getDataSource())
                        .tableName("CHAT_MEMORY; DROP TABLE USERS")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableName");
    }

    @Test
    void should_reject_injected_column_name() {
        assertThatThrownBy(() -> OracleChatMemoryStore.builder()
                        .dataSource(getDataSource())
                        .memoryIdColumnName("memory_id OR 1=1")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memoryIdColumnName");
    }

    private void createTable(String tableName, String memoryIdColumnName, String contentColumnName)
            throws SQLException {
        try (Connection connection = getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + tableName + " ("
                    + memoryIdColumnName + " VARCHAR2(255) PRIMARY KEY, "
                    + contentColumnName + " CLOB NOT NULL)");
        }
    }

    private void dropTableIfExists(String tableName) throws SQLException {
        try (Connection connection = getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + tableName);
        }
    }
}
