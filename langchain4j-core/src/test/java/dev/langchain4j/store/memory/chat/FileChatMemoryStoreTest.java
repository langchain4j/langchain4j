package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;


class FileChatMemoryStoreTest implements WithAssertions {

    static FileChatMemoryStore tempDirStore;

    static FileChatMemoryStore fixedDirStore;

    static FileChatMemoryStore txtExtMemoryStore;


    @BeforeAll
    static void init() {
        tempDirStore = FileChatMemoryStore.builder().build();
        String storageDirectory = System.getProperty("user.dir");
        fixedDirStore = FileChatMemoryStore.builder().storageDirectory(storageDirectory).build();
        txtExtMemoryStore = FileChatMemoryStore.builder().storageDirectory(storageDirectory).fileExtension(".txt").build();
    }

    @Test
    public void test_constructor() {
        assertThat(tempDirStore.getFileExtension())
                .isEqualTo(fixedDirStore.getFileExtension());
        assertThat(fixedDirStore.getStorageDirectory())
                .isEqualTo(txtExtMemoryStore.getStorageDirectory());
        assertThat(fixedDirStore.getFileExtension())
                .isNotEqualTo(txtExtMemoryStore.getFileExtension());
    }

    @Test
    public void test_store() {
        store(tempDirStore);
        store(fixedDirStore);
        store(txtExtMemoryStore);
    }

    public void store(ChatMemoryStore store) {
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