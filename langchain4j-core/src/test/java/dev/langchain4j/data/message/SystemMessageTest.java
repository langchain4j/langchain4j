package dev.langchain4j.data.message;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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

        assertThat(message.attributes()).isEmpty();

        assertThat(message).hasToString("SystemMessage { text = \"text\", attributes = {} }");
    }

    @Test
    void should_carry_attributes() {
        SystemMessage message = SystemMessage.builder()
                .text("text")
                .attributes(Map.of("prompt_cache_breakpoint", "explicit"))
                .build();

        assertThat(message.text()).isEqualTo("text");
        assertThat(message.attributes()).containsExactly(entry("prompt_cache_breakpoint", "explicit"));
        assertThat(message.attribute("prompt_cache_breakpoint", String.class)).isEqualTo("explicit");
        assertThat(message.attribute("missing", String.class)).isNull();
    }

    @Test
    void should_expose_mutable_attributes() {
        SystemMessage message = SystemMessage.from("text");

        message.attributes().put("prompt_cache_breakpoint", "explicit");

        assertThat(message.attribute("prompt_cache_breakpoint", String.class)).isEqualTo("explicit");
    }

    @Test
    void should_copy_via_to_builder() {
        SystemMessage message = SystemMessage.builder()
                .text("text")
                .attributes(Map.of("key", "value"))
                .build();

        assertThat(message.toBuilder().build()).isEqualTo(message);
        assertThat(message.toBuilder().text("other").build().text()).isEqualTo("other");
    }

    @Test
    void should_not_be_equal_when_attributes_differ() {
        SystemMessage withAttributes = SystemMessage.builder()
                .text("text")
                .attributes(Map.of("key", "value"))
                .build();

        assertThat(withAttributes)
                .isNotEqualTo(SystemMessage.from("text"))
                .doesNotHaveSameHashCodeAs(SystemMessage.from("text"));
    }

    @Test
    void should_fail_when_text_is_blank() {
        assertThatThrownBy(() -> SystemMessage.builder().text(" ").build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
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
