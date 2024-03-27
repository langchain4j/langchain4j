package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;
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
    static final Path memoryFilePath =
            Paths.get(tempStorageDirectory, randomDirName, "foo" + FileChatMemoryStore.FILE_EXTENSION);

    @AfterAll
    static void clear() throws IOException {
        Files.deleteIfExists(directoryPath);
    }

    @Test
    public void testConstructor() {
        FileChatMemoryStore fixedDirStore = new FileChatMemoryStore(directoryPath.toString());
        FileChatMemoryStore notExistDirStore = FileChatMemoryStore.builder()
                .storageDirectory(directoryPath.toString())
                .build();

        assertThat(fixedDirStore.getStorageDirectory()).endsWith(notExistDirStore.getStorageDirectory());
        assertThat(new File(notExistDirStore.getStorageDirectory())).exists();
    }

    @Test
    public void testStore() {
        FileChatMemoryStore store = FileChatMemoryStore.builder().build();

        assertThat(store.getMessages("foo")).isEmpty();

        store.updateMessages("foo", Arrays.asList(new UserMessage("abc def"), new AiMessage("ghi jkl")));

        assertThat(store.getMessages("foo")).containsExactly(new UserMessage("abc def"), new AiMessage("ghi jkl"));

        store.deleteMessages("foo");

        assertThat(store.getMessages("foo")).isEmpty();
    }

    @Test
    void illegalArgumentExceptionTest() throws IOException {
        File tempFile = File.createTempFile("temp", ".txt");
        String tempFileAbsolutePath = tempFile.getAbsolutePath();
        IllegalArgumentException illegalArgumentException =
                catchIllegalArgumentException(() -> new FileChatMemoryStore(tempFileAbsolutePath));
        assertThat(illegalArgumentException).isNotNull();
    }

    @Test
    void createDriExceptionTest() {
        FileChatMemoryStore.Builder builder = FileChatMemoryStore.builder().storageDirectory(directoryPath.toString());

        try (MockedStatic<Files> mockFiles = mockStatic(Files.class)) {
            mockFiles.when(() -> Files.createDirectories(directoryPath)).thenThrow(IOException.class);
            RuntimeException runtimeException = catchRuntimeException(builder::build);
            assertThat(runtimeException).isNotNull();
        }
    }

    @Test
    void writeExceptionTest() {
        List<ChatMessage> messages = Arrays.asList(new UserMessage("abc def"), new AiMessage("ghi jkl"));
        String messagesToJson = ChatMessageSerializer.messagesToJson(messages);

        FileChatMemoryStore.Builder builder = FileChatMemoryStore.builder().storageDirectory(directoryPath.toString());
        try (MockedStatic<Files> mockFiles = mockStatic(Files.class)) {
            FileChatMemoryStore store = builder.build();
            mockFiles
                    .when(() -> Files.write(memoryFilePath, messagesToJson.getBytes(StandardCharsets.UTF_8)))
                    .thenThrow(IOException.class);
            RuntimeException runtimeException = catchRuntimeException(() -> store.updateMessages("foo", messages));
            assertThat(runtimeException).isNotNull();
        }
    }

    @Test
    void readExceptionTest() {
        FileChatMemoryStore.Builder builder = FileChatMemoryStore.builder().storageDirectory(directoryPath.toString());
        try (MockedStatic<Files> mockFiles = mockStatic(Files.class)) {
            mockFiles.when(() -> Files.readAllBytes(memoryFilePath)).thenThrow(IOException.class);
            FileChatMemoryStore store = builder.build();
            List<ChatMessage> messages = store.getMessages("foo");
            assertThat(messages).isEmpty();
        }
    }

    @Test
    void delDriExceptionTest() {
        FileChatMemoryStore store = FileChatMemoryStore.builder()
                .storageDirectory(directoryPath.toString())
                .build();
        try (MockedStatic<Files> mockFiles = mockStatic(Files.class)) {
            mockFiles.when(() -> Files.deleteIfExists(memoryFilePath)).thenThrow(IOException.class);
            Throwable throwable = catchThrowable(() -> store.deleteMessages("foo"));
            assertThat(throwable).isNull();
        }
    }
}
