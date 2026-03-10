package dev.langchain4j.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMemoryTest {

    private TestChatMemory memory;

    @BeforeEach
    void setUp() {
        memory = new TestChatMemory();
    }

    @Test
    void add_varargs_should_delegate_to_add_iterable() {
        ChatMessage m1 = UserMessage.from("1");
        ChatMessage m2 = UserMessage.from("2");

        memory.add(m1, m2);

        assertThat(memory.messages())
                .containsExactly(m1, m2);
    }

    @Test
    void add_varargs_should_ignore_null_array() {
        memory.add((ChatMessage[]) null);

        assertThat(memory.messages()).isEmpty();
    }

    @Test
    void add_varargs_should_ignore_empty_array() {
        memory.add();

        assertThat(memory.messages()).isEmpty();
    }

    @Test
    void add_iterable_should_add_each_message() {
        ChatMessage m1 = UserMessage.from("1");
        ChatMessage m2 = UserMessage.from("2");

        memory.add(List.of(m1, m2));

        assertThat(memory.messages())
                .containsExactly(m1, m2);
    }

    @Test
    void add_iterable_should_ignore_null_iterable() {
        memory.add((Iterable<ChatMessage>) null);

        assertThat(memory.messages()).isEmpty();
    }

    @Test
    void set_varargs_should_replace_existing_messages() {
        memory.add(UserMessage.from("old"));

        ChatMessage m1 = UserMessage.from("1");
        ChatMessage m2 = UserMessage.from("2");

        memory.set(m1, m2);

        assertThat(memory.clearCalled).isTrue();
        assertThat(memory.messages())
                .containsExactly(m1, m2);
    }

    @Test
    void set_varargs_should_fail_on_null() {
        assertThatThrownBy(() -> memory.set((ChatMessage[]) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void set_varargs_should_fail_on_empty() {
        assertThatThrownBy(() -> memory.set())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void set_iterable_should_replace_existing_messages() {
        memory.add(UserMessage.from("old"));

        ChatMessage m1 = UserMessage.from("1");
        ChatMessage m2 = UserMessage.from("2");

        memory.set(List.of(m1, m2));

        assertThat(memory.clearCalled).isTrue();
        assertThat(memory.messages())
                .containsExactly(m1, m2);
    }

    @Test
    void set_iterable_should_fail_on_null() {
        assertThatThrownBy(() -> memory.set((Iterable<ChatMessage>) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void set_iterable_should_fail_on_empty_iterable() {
        Iterable<ChatMessage> emptyIterable = new Iterable<>() {
            @Override
            public Iterator<ChatMessage> iterator() {
                return List.<ChatMessage>of().iterator();
            }
        };

        assertThatThrownBy(() -> memory.set(emptyIterable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    /**
     * Minimal test implementation to observe default-method behavior.
     */
    private static class TestChatMemory implements ChatMemory {

        private final List<ChatMessage> messages = new ArrayList<>();
        private boolean clearCalled = false;

        @Override
        public Object id() {
            return "test";
        }

        @Override
        public void add(ChatMessage message) {
            messages.add(message);
        }

        @Override
        public List<ChatMessage> messages() {
            return new ArrayList<>(messages);
        }

        @Override
        public void clear() {
            clearCalled = true;
            messages.clear();
        }
    }
}
