package dev.langchain4j.service.memory;

import static dev.langchain4j.service.memory.ChatMemoryService.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatMemoryServiceTest {

    @Test
    void single_memory_mode_getChatMemory_returns_the_default_memory_for_any_memoryId() {
        // given
        ChatMemory defaultChatMemory = MessageWindowChatMemory.builder().maxMessages(10).build();
        ChatMemoryService service = new ChatMemoryService(defaultChatMemory);

        // when-then
        // The memory is returned not only for the DEFAULT literal, but also for an equal-but-distinct
        // String (the bug was a reference comparison) and for any other id, consistently with
        // getOrCreateChatMemory().
        assertThat(service.getChatMemory(DEFAULT)).isSameAs(defaultChatMemory);
        assertThat(service.getChatMemory(new String(DEFAULT))).isSameAs(defaultChatMemory);
        assertThat(service.getChatMemory("any-other-id")).isSameAs(defaultChatMemory);
        assertThat(service.getChatMemory(42L)).isSameAs(defaultChatMemory);
        assertThat(service.getOrCreateChatMemory(new String(DEFAULT))).isSameAs(defaultChatMemory);
        assertThat(service.getOrCreateChatMemory("any-other-id")).isSameAs(defaultChatMemory);
    }

    @Test
    void single_memory_mode_clearAll_clears_the_default_memory_without_NPE() {
        // given
        ChatMemory defaultChatMemory = MessageWindowChatMemory.builder().maxMessages(10).build();
        defaultChatMemory.add(UserMessage.from("hello"));
        assertThat(defaultChatMemory.messages()).hasSize(1);
        ChatMemoryService service = new ChatMemoryService(defaultChatMemory);

        // when
        service.clearAll();

        // then
        assertThat(defaultChatMemory.messages()).isEmpty();
    }

    @Test
    void provider_mode_getChatMemory_returns_memory_only_after_it_was_created() {
        // given
        ChatMemoryProvider provider = memoryId -> MessageWindowChatMemory.builder().maxMessages(10).build();
        ChatMemoryService service = new ChatMemoryService(provider);

        // when-then
        assertThat(service.getChatMemory("id1")).isNull();
        service.getOrCreateChatMemory("id1");
        assertThat(service.getChatMemory("id1")).isNotNull();
        assertThat(service.getChatMemory("id2")).isNull();
    }

    @Test
    void provider_mode_clearAll_clears_all_created_memories() {
        // given
        ChatMemoryProvider provider = memoryId -> MessageWindowChatMemory.builder().maxMessages(10).build();
        ChatMemoryService service = new ChatMemoryService(provider);

        ChatMemory memory1 = service.getOrCreateChatMemory("id1");
        ChatMemory memory2 = service.getOrCreateChatMemory("id2");
        memory1.add(UserMessage.from("hello"));
        memory2.add(UserMessage.from("hi"));
        assertThat(memory1.messages()).hasSize(1);
        assertThat(memory2.messages()).hasSize(1);

        // when
        service.clearAll();

        // then
        assertThat(memory1.messages()).isEmpty();
        assertThat(memory2.messages()).isEmpty();
        assertThat(service.getChatMemoryIDs()).isEmpty();
        assertThat(service.getChatMemories()).isEmpty();
    }

    @Test
    void provider_mode_evictChatMemory_removes_only_the_requested_memory() {
        // given
        ChatMemoryProvider provider = memoryId -> MessageWindowChatMemory.builder().maxMessages(10).build();
        ChatMemoryService service = new ChatMemoryService(provider);

        ChatMemory memory1 = service.getOrCreateChatMemory("id1");
        service.getOrCreateChatMemory("id2");

        // when
        ChatMemory evicted = service.evictChatMemory("id1");

        // then
        assertThat(evicted).isSameAs(memory1);
        assertThat(service.getChatMemory("id1")).isNull();
        assertThat(service.getChatMemory("id2")).isNotNull();
    }

    @Test
    void single_memory_mode_and_provider_mode_are_mutually_exclusive() {
        // ChatMemoryService can only be configured with either a single ChatMemory or a provider.
        ChatMemoryService single = new ChatMemoryService(MessageWindowChatMemory.builder().maxMessages(10).build());
        assertThat(single.getChatMemory(DEFAULT)).isNotNull();

        ChatMemoryService provider = new ChatMemoryService(memoryId -> MessageWindowChatMemory.builder().maxMessages(10).build());
        assertThat(provider.getChatMemory(DEFAULT)).isNull();

        List<ChatMessage> messages = provider.getOrCreateChatMemory(DEFAULT).messages();
        assertThat(messages).isEmpty();
    }
}
