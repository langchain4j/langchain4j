package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

class FileSystemChatMemoryStoreTest implements WithAssertions {

    @ParameterizedTest
    @MethodSource("provideDirectoryPaths")
    void should_create_directory_structure(String pathDescription, Path subdirectory, @TempDir Path tempDir) {
        Path targetDir = subdirectory != null ? tempDir.resolve(subdirectory) : tempDir;
        
        if (!targetDir.equals(tempDir)) {
            assertThat(targetDir).doesNotExist();
        }

        new FileSystemChatMemoryStore(targetDir);

        assertThat(targetDir).exists().isDirectory();
    }

    static Stream<Arguments> provideDirectoryPaths() {
        return Stream.of(
                Arguments.of("new directory", Path.of("chat-memory")),
                Arguments.of("existing directory", null),
                Arguments.of("nested directory", Path.of("level1").resolve("level2").resolve("chat-memory"))
        );
    }

    @Test
    void should_throw_when_directory_is_null() {
        assertThatThrownBy(() -> new FileSystemChatMemoryStore(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void should_return_empty_list_for_non_existing_memory(@TempDir Path tempDir) {
        FileSystemChatMemoryStore store = new FileSystemChatMemoryStore(tempDir);

        List<ChatMessage> messages = store.getMessages("non-existing");

        assertThat(messages).isNotNull().isEmpty();
    }

    @Test
    void should_store_retrieve_and_overwrite_messages(@TempDir Path tempDir) {
        FileSystemChatMemoryStore store = new FileSystemChatMemoryStore(tempDir);
        List<ChatMessage> firstMessages = Arrays.asList(
                new UserMessage("Hello"),
                new AiMessage("Hi there!")
        );
        List<ChatMessage> secondMessages = Arrays.asList(
                new UserMessage("Second message"),
                new AiMessage("Second response")
        );

        // Store and retrieve
        store.updateMessages("conversation-1", firstMessages);
        assertThat(store.getMessages("conversation-1")).isEqualTo(firstMessages);

        // Overwrite and verify
        store.updateMessages("conversation-1", secondMessages);
        assertThat(store.getMessages("conversation-1")).isEqualTo(secondMessages);

        // Multiple successive updates
        for (int i = 0; i < 10; i++) {
            store.updateMessages("conversation-1", Arrays.asList(new UserMessage("Message " + i)));
        }
        assertThat(store.getMessages("conversation-1")).containsExactly(new UserMessage("Message 9"));
    }

    @Test
    void should_delete_messages(@TempDir Path tempDir) {
        FileSystemChatMemoryStore store = new FileSystemChatMemoryStore(tempDir);
        List<ChatMessage> messages = Arrays.asList(new UserMessage("Hello"));

        // Delete existing memory
        store.updateMessages("conversation-1", messages);
        assertThat(store.getMessages("conversation-1")).isNotEmpty();
        store.deleteMessages("conversation-1");
        assertThat(store.getMessages("conversation-1")).isEmpty();

        // Delete non-existing memory should not throw
        assertThatCode(() -> store.deleteMessages("non-existing"))
                .doesNotThrowAnyException();
    }

    @Test
    void should_persist_across_store_instances(@TempDir Path tempDir) {
        List<ChatMessage> messages = Arrays.asList(
                new UserMessage("Persisted message"),
                new AiMessage("Persisted response")
        );

        // First store instance
        FileSystemChatMemoryStore store1 = new FileSystemChatMemoryStore(tempDir);
        store1.updateMessages("conversation-1", messages);

        // Second store instance (simulating application restart)
        FileSystemChatMemoryStore store2 = new FileSystemChatMemoryStore(tempDir);
        List<ChatMessage> retrieved = store2.getMessages("conversation-1");

        assertThat(retrieved).isEqualTo(messages);
    }

    @ParameterizedTest
    @MethodSource("provideMemoryIds")
    void should_handle_various_memory_ids(Object memoryId, String expectedFilename, @TempDir Path tempDir) {
        FileSystemChatMemoryStore store = new FileSystemChatMemoryStore(tempDir);
        List<ChatMessage> messages = Arrays.asList(new UserMessage("Test message"));

        store.updateMessages(memoryId, messages);
        List<ChatMessage> retrieved = store.getMessages(memoryId);

        assertThat(retrieved).isEqualTo(messages);
        if (expectedFilename != null) {
            assertThat(tempDir.resolve(expectedFilename)).exists();
        }
    }

    static Stream<Arguments> provideMemoryIds() {
        return Stream.of(
                Arguments.of("customer/123:456*789?abc\"def<ghi>jkl|mno\\pqr", 
                           "customer_123_456_789_abc_def_ghi_jkl_mno_pqr.json"),
                Arguments.of(12345, "12345.json"),
                Arguments.of("conversation-1", "conversation-1.json"),
                Arguments.of("conversation-2", "conversation-2.json"),
                Arguments.of("conversation-3", "conversation-3.json")
        );
    }

    @ParameterizedTest
    @MethodSource("provideMessageLists")
    void should_handle_various_message_contents(String description, List<ChatMessage> messages, @TempDir Path tempDir) {
        FileSystemChatMemoryStore store = new FileSystemChatMemoryStore(tempDir);

        store.updateMessages("test-" + description, messages);
        List<ChatMessage> retrieved = store.getMessages("test-" + description);

        assertThat(retrieved).isEqualTo(messages);
    }

    static Stream<Arguments> provideMessageLists() {
        List<ChatMessage> largeList = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeList.add(new UserMessage("User message " + i));
            largeList.add(new AiMessage("AI response " + i));
        }

        return Stream.of(
                Arguments.of("utf8", Arrays.asList(
                        new UserMessage("Hello 你好 مرحبا שלום"),
                        new AiMessage("Response with emoji 😀🎉")
                )),
                Arguments.of("empty", new ArrayList<ChatMessage>()),
                Arguments.of("large", largeList)
        );
    }

    @Test
    void should_create_valid_json_files(@TempDir Path tempDir) throws IOException {
        FileSystemChatMemoryStore store = new FileSystemChatMemoryStore(tempDir);
        List<ChatMessage> messages = Arrays.asList(
                new UserMessage("Test"),
                new AiMessage("Response")
        );

        store.updateMessages("test", messages);

        Path file = tempDir.resolve("test.json");
        assertThat(file).exists();

        String content = Files.readString(file);
        assertThat(content).isNotEmpty();

        // Verify the content is valid by deserializing it
        String expectedJson = ChatMessageSerializer.messagesToJson(messages);
        assertThat(content).isEqualTo(expectedJson);
    }

    @ParameterizedTest
    @MethodSource("provideConcurrentScenarios")
    void should_handle_concurrent_operations(String scenario, 
                                             int threadCount, int operationsPerThread,
                                             boolean useSameMemoryId, @TempDir Path tempDir) throws InterruptedException {
        FileSystemChatMemoryStore store = new FileSystemChatMemoryStore(tempDir);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String memoryId = useSameMemoryId ? "shared-memory" : "thread-" + threadId + "-memory-" + j;
                        List<ChatMessage> messages = Arrays.asList(
                                new UserMessage("Thread-" + threadId + "-Op-" + j)
                        );
                        store.updateMessages(memoryId, messages);
                        store.getMessages(memoryId);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(exceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(threadCount * operationsPerThread);
    }

    static Stream<Arguments> provideConcurrentScenarios() {
        return Stream.of(
                Arguments.of("same memory", 10, 20, true),
                Arguments.of("different memories", 10, 50, false)
        );
    }

    @Test
    void should_handle_mixed_concurrent_operations(@TempDir Path tempDir) throws InterruptedException {
        FileSystemChatMemoryStore store = new FileSystemChatMemoryStore(tempDir);
        int threadCount = 8;
        String sharedMemoryId = "shared-memory";

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        // Pre-populate the shared memory
        store.updateMessages(sharedMemoryId, Arrays.asList(new UserMessage("Initial")));

        for (int i = 0; i < threadCount; i++) {
            int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 20; j++) {
                        switch (j % 3) {
                            case 0: // Write
                                store.updateMessages(sharedMemoryId, Arrays.asList(
                                        new UserMessage("Update from thread " + threadId)
                                ));
                                break;
                            case 1: // Read
                                List<ChatMessage> messages = store.getMessages(sharedMemoryId);
                                assertThat(messages).isNotNull();
                                break;
                            case 2: // Delete and recreate
                                store.deleteMessages(sharedMemoryId);
                                store.updateMessages(sharedMemoryId, Arrays.asList(
                                        new UserMessage("Recreated by thread " + threadId)
                                ));
                                break;
                        }
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Verify no exceptions occurred
        assertThat(exceptions).isEmpty();

        // Verify the file is not corrupted
        List<ChatMessage> finalMessages = store.getMessages(sharedMemoryId);
        assertThat(finalMessages).isNotNull();
    }

    @Test
    void should_not_leave_temporary_files_on_successful_write(@TempDir Path tempDir) throws InterruptedException {
        FileSystemChatMemoryStore store = new FileSystemChatMemoryStore(tempDir);
        List<ChatMessage> messages = Arrays.asList(new UserMessage("Test"));

        store.updateMessages("test", messages);

        // Give filesystem time to complete any pending operations
        Thread.sleep(100);

        // Check for temporary files
        assertThat(tempDir.toFile().listFiles())
                .filteredOn(file -> file.getName().startsWith(".tmp-"))
                .isEmpty();
    }

    @Test
    void should_normalize_directory_path(@TempDir Path tempDir) {
        Path unnormalizedPath = tempDir.resolve("./foo/../bar");
        FileSystemChatMemoryStore store = new FileSystemChatMemoryStore(unnormalizedPath);

        List<ChatMessage> messages = Arrays.asList(new UserMessage("Test"));
        store.updateMessages("test", messages);

        // Verify the file was created in the normalized path
        Path normalizedPath = tempDir.resolve("bar");
        Path expectedFile = normalizedPath.resolve("test.json");
        assertThat(expectedFile).exists();
    }
}
