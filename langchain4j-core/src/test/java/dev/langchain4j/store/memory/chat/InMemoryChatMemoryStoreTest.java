package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class InMemoryChatMemoryStoreTest implements WithAssertions {
    @Test
    void test() {
        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        assertThat(store.getMessages("foo")).isEmpty();

        store.updateMessages("foo", Arrays.asList(new UserMessage("abc def"), new AiMessage("ghi jkl")));

        assertThat(store.getMessages("foo")).containsExactly(new UserMessage("abc def"), new AiMessage("ghi jkl"));

        store.deleteMessages("foo");

        assertThat(store.getMessages("foo")).isEmpty();
    }

    @Test
    void async() {
        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        assertThat(getAsync(store, "foo")).isEmpty();

        List<ChatMessage> messages = Arrays.asList(new UserMessage("abc def"), new AiMessage("ghi jkl"));
        store.updateMessagesAsync("foo", messages).toCompletableFuture().join();

        assertThat(getAsync(store, "foo")).containsExactlyElementsOf(messages);
        // async and sync views are consistent
        assertThat(store.getMessages("foo")).containsExactlyElementsOf(messages);

        store.deleteMessagesAsync("foo").toCompletableFuture().join();

        assertThat(getAsync(store, "foo")).isEmpty();
    }

    private static List<ChatMessage> getAsync(InMemoryChatMemoryStore store, Object memoryId) {
        return store.getMessagesAsync(memoryId).toCompletableFuture().join();
    }
}
