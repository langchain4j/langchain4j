package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.SneakyThrows;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;

class FileChatMemoryStoreTest implements WithAssertions {

    static String tempFileAbsolutePath;
    static final String randomDirName = UUID.randomUUID().toString();
    static final String tempStorageDirectory = System.getProperty("java.io.tmpdir");

    @AfterAll
    @SneakyThrows
    static void clear() {
        Files.deleteIfExists(Paths.get(tempStorageDirectory, randomDirName));
        Files.deleteIfExists(Paths.get(tempFileAbsolutePath));
    }

    @Test
    public void test_constructor() {
        FileChatMemoryStore tempDirStore = FileChatMemoryStore.builder().build();
        FileChatMemoryStore fixedDirStore = FileChatMemoryStore.builder().storageDirectory(tempStorageDirectory).build();
        FileChatMemoryStore txtExtMemoryStore = FileChatMemoryStore.builder().storageDirectory(tempStorageDirectory).fileExtension(".txt").build();
        FileChatMemoryStore notExistDirStore = FileChatMemoryStore.builder().storageDirectory(tempStorageDirectory + File.separator + randomDirName).fileExtension(".txt").build();

        assertThat(tempDirStore.getFileExtension()).isEqualTo(fixedDirStore.getFileExtension());
        assertThat(fixedDirStore.getStorageDirectory()).isEqualTo(txtExtMemoryStore.getStorageDirectory());
        assertThat(fixedDirStore.getFileExtension()).isNotEqualTo(txtExtMemoryStore.getFileExtension());
        assertThat(new File(notExistDirStore.getStorageDirectory())).exists();
    }

    @Test
    @SneakyThrows
    void exceptionTesting() {
        File tempFile = File.createTempFile("temp", ".txt");
        tempFileAbsolutePath = tempFile.getAbsolutePath();
        IllegalArgumentException illegalArgumentException = catchIllegalArgumentException(() -> new FileChatMemoryStore(tempFileAbsolutePath, ".json"));
        assertThat(illegalArgumentException).isNotNull();
    }

    @Test
    public void test_store() {
        FileChatMemoryStore store = FileChatMemoryStore.builder().build();

        assertThat(store.getMessages("foo")).isEmpty();

        store.updateMessages("foo", Arrays.asList(new UserMessage("abc def"), new AiMessage("ghi jkl")));

        assertThat(store.getMessages("foo")).containsExactly(new UserMessage("abc def"), new AiMessage("ghi jkl"));

        store.deleteMessages("foo");

        assertThat(store.getMessages("foo")).isEmpty();
    }
}