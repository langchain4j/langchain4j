package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;
import lombok.SneakyThrows;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mockStatic;

class FileChatMemoryStoreTest implements WithAssertions {

    static final String randomDirName = UUID.randomUUID().toString();
    static final String tempStorageDirectory = System.getProperty("java.io.tmpdir");

    static final Path directoryPath = Paths.get(tempStorageDirectory, randomDirName);
    static final Path memoryFilePath = Paths.get(tempStorageDirectory, randomDirName, "foo.txt");

    @AfterAll
    @SneakyThrows
    static void clear() {
        Files.deleteIfExists(directoryPath);
    }

    @Test
    public void test_constructor() {
        FileChatMemoryStore tempDirStore = FileChatMemoryStore.builder().build();
        FileChatMemoryStore fixedDirStore = FileChatMemoryStore.builder()
                .storageDirectory(tempStorageDirectory)
                .build();
        FileChatMemoryStore txtExtMemoryStore = FileChatMemoryStore.builder()
                .storageDirectory(tempStorageDirectory)
                .fileExtension(".txt")
                .build();
        FileChatMemoryStore notExistDirStore = FileChatMemoryStore.builder()
                .storageDirectory(directoryPath.toString())
                .fileExtension("txt")
                .build();

        assertThat(tempDirStore.getFileExtension()).isEqualTo(fixedDirStore.getFileExtension());
        assertThat(fixedDirStore.getStorageDirectory()).isEqualTo(txtExtMemoryStore.getStorageDirectory());
        assertThat(fixedDirStore.getFileExtension()).isNotEqualTo(txtExtMemoryStore.getFileExtension());
        assertThat(new File(notExistDirStore.getStorageDirectory())).exists();
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

    @Test
    @SneakyThrows
    void illegalArgumentExceptionTesting() {
        File tempFile = File.createTempFile("temp", ".txt");
        String tempFileAbsolutePath = tempFile.getAbsolutePath();
        IllegalArgumentException illegalArgumentException =
                catchIllegalArgumentException(() -> new FileChatMemoryStore(tempFileAbsolutePath, ".json"));
        assertThat(illegalArgumentException).isNotNull();
    }

    @Test
    @SneakyThrows
    void createDriExceptionTesting() {
        FileChatMemoryStore.Builder builder = FileChatMemoryStore.builder()
                .storageDirectory(directoryPath.toString())
                .fileExtension(".txt");

        try (MockedStatic<Files> mockFiles = mockStatic(Files.class)) {
            mockFiles.when(() -> Files.createDirectories(directoryPath)).thenThrow(IOException.class);
            RuntimeException runtimeException = catchRuntimeException(builder::build);
            assertThat(runtimeException).isNotNull();
        }
    }

    @Test
    @SneakyThrows
    void writeExceptionTesting() {
        List<ChatMessage> messages = Arrays.asList(new UserMessage("abc def"), new AiMessage("ghi jkl"));
        String messagesToJson = ChatMessageSerializer.messagesToJson(messages);

        FileChatMemoryStore store = new FileChatMemoryStore(directoryPath.toString(), ".txt");
        try (MockedStatic<Files> mockFiles = mockStatic(Files.class)) {
            mockFiles
                    .when(() -> Files.write(memoryFilePath, messagesToJson.getBytes(StandardCharsets.UTF_8)))
                    .thenThrow(IOException.class);
            RuntimeException runtimeException = catchRuntimeException(() -> store.updateMessages("foo", messages));
            assertThat(runtimeException).isNotNull();
        }
    }

    @Test
    @SneakyThrows
    void readExceptionTesting() {
        FileChatMemoryStore store = new FileChatMemoryStore(directoryPath.toString(), ".txt");
        try (MockedStatic<Files> mockFiles = mockStatic(Files.class)) {
            mockFiles.when(() -> Files.readAllBytes(memoryFilePath)).thenThrow(IOException.class);
            List<ChatMessage> messages = store.getMessages("foo");
            assertThat(messages).isEmpty();
        }
    }

    @Test
    @SneakyThrows
    void delDriExceptionTesting() {
        FileChatMemoryStore store = new FileChatMemoryStore(directoryPath.toString(), ".txt");
        try (MockedStatic<Files> mockFiles = mockStatic(Files.class)) {
            mockFiles.when(() -> Files.deleteIfExists(memoryFilePath)).thenThrow(IOException.class);
            Throwable throwable = catchThrowable(() -> store.deleteMessages("foo"));
            assertThat(throwable).isNull();
        }
    }
}