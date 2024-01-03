package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class InMemoryChatMemoryStoreTest implements WithAssertions {
    @Test
    public void test() {
        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        assertThat(store.getMessages("foo")).isEmpty();

        store.updateMessages(
                "foo",
                Arrays.asList(
                        new UserMessage("abc def"),
                        new AiMessage("ghi jkl")));


        assertThat(store.getMessages("foo")).containsExactly(
                new UserMessage("abc def"),
                new AiMessage("ghi jkl"));

        store.deleteMessages("foo");

        assertThat(store.getMessages("foo")).isEmpty();
    }
}