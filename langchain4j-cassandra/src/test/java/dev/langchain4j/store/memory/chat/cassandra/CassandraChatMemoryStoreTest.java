package dev.langchain4j.store.memory.chat.cassandra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Statement;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CassandraChatMemoryStoreTest {

    private PreparedStatement preparedStatement;
    private CassandraChatMemoryStore store;

    @BeforeEach
    void setUp() {
        CqlSession session = mock(CqlSession.class);
        when(session.getKeyspace()).thenReturn(Optional.of(CqlIdentifier.fromCql("ks")));
        preparedStatement = mock(PreparedStatement.class);
        when(session.prepare(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.bind(any(Object[].class))).thenReturn(mock(BoundStatement.class));
        when(session.execute(any(Statement.class))).thenReturn(mock(ResultSet.class));
        store = new CassandraChatMemoryStore(session, "message_store");
    }

    @Test
    void getMessages_accepts_integer_memory_id() {
        assertThat(store.getMessages(42)).isEmpty();
        verify(preparedStatement).bind("42");
    }

    @Test
    void getMessages_accepts_uuid_memory_id() {
        UUID memoryId = UUID.randomUUID();

        assertThat(store.getMessages(memoryId)).isEmpty();
        verify(preparedStatement).bind(memoryId.toString());
    }

    @Test
    void getMessages_still_accepts_string_memory_id() {
        assertThat(store.getMessages("chat-1")).isEmpty();
        verify(preparedStatement).bind("chat-1");
    }

    @Test
    void updateMessages_accepts_integer_memory_id() {
        store.updateMessages(42, List.of(UserMessage.from("hello")));

        verify(preparedStatement).bind(eq("42"), any(UUID.class), anyString());
    }

    @Test
    void deleteMessages_accepts_integer_memory_id() {
        store.deleteMessages(42);

        verify(preparedStatement).bind("42");
    }

    @Test
    void getMessages_rejects_null_memory_id() {
        assertThatThrownBy(() -> store.getMessages(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("'memoryId' must not be null");
    }
}
