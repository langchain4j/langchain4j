package dev.langchain4j.data.message;

import java.util.Collections;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class SystemMessageTest implements WithAssertions {
    @Test
    void builders() {
        assertThat(new SystemMessage("text"))
                .isEqualTo(SystemMessage.from("text"))
                .isEqualTo(SystemMessage.systemMessage("text"));
    }

    @Test
    void methods() {
        SystemMessage message = new SystemMessage("text");
        assertThat(message.text()).isEqualTo("text");
        assertThat(message.type()).isEqualTo(ChatMessageType.SYSTEM);

        assertThat(message).hasToString("SystemMessage { text = \"text\" }");
    }

    @Test
    void equals_hash_code() {
        SystemMessage s1 = new SystemMessage("text");
        SystemMessage s2 = new SystemMessage("text");

        SystemMessage s3 = new SystemMessage("text2");
        SystemMessage s4 = new SystemMessage("text2");

        assertThat(s1)
                .isEqualTo(s1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(s2)
                .hasSameHashCodeAs(s2)
                .isNotEqualTo(s3)
                .doesNotHaveSameHashCodeAs(s3);

        assertThat(s3).isEqualTo(s3).isEqualTo(s4).hasSameHashCodeAs(s4);
    }

    @Test
    void find_with_null_list() {
        assertThatThrownBy(() -> SystemMessage.findFirst(null)).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> SystemMessage.findLast(null)).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> SystemMessage.findAll(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void find_with_empty_list() {
        List<ChatMessage> emptyList = Collections.emptyList();
        assertThat(SystemMessage.findFirst(emptyList)).isEmpty();
        assertThat(SystemMessage.findLast(emptyList)).isEmpty();
        assertThat(SystemMessage.findAll(emptyList)).isEmpty();
    }

    @Test
    void find_with_single_system_message() {
        SystemMessage systemMessage = new SystemMessage("system text");
        List<ChatMessage> messages = List.of(systemMessage);

        assertThat(SystemMessage.findFirst(messages)).isPresent().contains(systemMessage);

        assertThat(SystemMessage.findLast(messages)).isPresent().contains(systemMessage);

        assertThat(SystemMessage.findAll(messages)).hasSize(1).containsExactly(systemMessage);
    }

    @Test
    void find_with_single_user_message() {
        UserMessage userMessage = new UserMessage("user text");
        List<ChatMessage> messages = List.of(userMessage);

        assertThat(SystemMessage.findFirst(messages)).isEmpty();
        assertThat(SystemMessage.findLast(messages)).isEmpty();
        assertThat(SystemMessage.findAll(messages)).isEmpty();
    }

    @Test
    void find_with_mixed_messages() {
        SystemMessage system1 = new SystemMessage("system 1");
        UserMessage user1 = new UserMessage("user 1");
        AiMessage ai1 = new AiMessage("ai 1");
        SystemMessage system2 = new SystemMessage("system 2");
        UserMessage user2 = new UserMessage("user 2");
        AiMessage ai2 = new AiMessage("ai 2");
        SystemMessage system3 = new SystemMessage("system 3");

        List<ChatMessage> messages = List.of(user1, system1, ai1, system2, user2, ai2, system3);

        assertThat(SystemMessage.findFirst(messages)).isPresent().contains(system1);

        assertThat(SystemMessage.findLast(messages)).isPresent().contains(system3);

        assertThat(SystemMessage.findAll(messages)).hasSize(3).containsExactly(system1, system2, system3);
    }
}
