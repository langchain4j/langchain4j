package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Exceptions.runtime;

/**
 * Implementation of {@link ChatMemoryStore} that stores state of {@link dev.langchain4j.memory.ChatMemory} (chat messages) a local file.
 * <p>
 * This mechanism is more reliable than {@link InMemoryChatMemoryStore}.
 */
public class FileChatMemoryStore implements ChatMemoryStore {
    private static final Logger log = LoggerFactory.getLogger(FileChatMemoryStore.class);

    private final String storageDirectory;

    public static final String FILE_EXTENSION = ".json";

    /**
     * Constructor for FileChatMemoryStore.
     *
     * @param storageDirectory Folder path where msg is stored.
     */
    public FileChatMemoryStore(String storageDirectory) {
        Path directoryPath = Paths.get(storageDirectory);
        if (Files.exists(directoryPath)) {
            if (!Files.isDirectory(directoryPath)) {
                throw illegalArgument("Storage directory path exists but is a file: %s", storageDirectory);
            }
        } else {
            try {
                Files.createDirectories(directoryPath);
            } catch (IOException e) {
                log.error("Failed to create directory: {}", directoryPath, e);
                throw runtime("Something is wrong, Failed to create folder %s", storageDirectory);
            }
            log.debug("Created directory {} to store messages", storageDirectory);
        }
        this.storageDirectory = storageDirectory;
    }

    public String getStorageDirectory() {
        return storageDirectory;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Path memoryFilePath = Paths.get(storageDirectory, memoryId + FILE_EXTENSION);
        if (Files.notExists(memoryFilePath)) {
            return Collections.emptyList();
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(memoryFilePath);
        } catch (IOException e) {
            log.warn("Loading messages from file {} failed.", memoryFilePath);
            return Collections.emptyList();
        }
        String fileContent = new String(bytes, StandardCharsets.UTF_8);
        return ChatMessageDeserializer.messagesFromJson(fileContent);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        Path memoryFilePath = Paths.get(storageDirectory, memoryId + FILE_EXTENSION);
        // TODO：Maybe it would be better to store it item by item.
        String messagesToJson = ChatMessageSerializer.messagesToJson(messages);
        try {
            Files.write(memoryFilePath, messagesToJson.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw runtime("Update messages to file {} failed.", memoryFilePath);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        Path memoryFilePath = Paths.get(storageDirectory, memoryId + FILE_EXTENSION);
        try {
            Files.deleteIfExists(memoryFilePath);
        } catch (IOException e) {
            log.warn("Delete memory file {} failed", memoryFilePath);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String storageDirectory = System.getProperty("java.io.tmpdir");

        /**
         * @param storageDirectory Local folder path where messages are stored.
         * @return builder
         */
        public Builder storageDirectory(String storageDirectory) {
            this.storageDirectory = storageDirectory;
            return this;
        }

        public FileChatMemoryStore build() {
            return new FileChatMemoryStore(storageDirectory);
        }
    }
}
