package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Implementation of {@link ChatMemoryStore} that stores state of {@link dev.langchain4j.memory.ChatMemory}
 * (chat messages) on the local file system.
 * <p>
 * Each chat memory is persisted as a separate JSON file within the specified directory.
 * Messages are serialized using {@link ChatMessageSerializer} and deserialized using {@link ChatMessageDeserializer}.
 * <p>
 * <b>Storage Layout:</b>
 * <pre>
 * chat-memory/
 *     conversation-1.json
 *     conversation-2.json
 *     customer-42.json
 * </pre>
 * <p>
 * <b>Persistence Behavior:</b>
 * <ul>
 *     <li>All files are encoded in UTF-8</li>
 *     <li>Updates overwrite the previous state (not append)</li>
 *     <li>Atomic file replacement is used to minimize corruption risk</li>
 *     <li>Memory IDs are sanitized to produce valid filenames</li>
 * </ul>
 * <p>
 * <b>Thread Safety:</b>
 * This implementation is thread-safe. Concurrent operations on different memory IDs do not block each other.
 * Concurrent operations on the same memory ID are serialized to prevent file corruption.
 */
public class FileSystemChatMemoryStore implements ChatMemoryStore {

    private static final String FILE_EXTENSION = ".json";
    private static final String TEMP_FILE_PREFIX = ".tmp-";
    private static final char[] ILLEGAL_FILENAME_CHARS = {'/', '\\', ':', '*', '?', '"', '<', '>', '|'};

    private final Path directory;
    private final Map<Path, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Constructs a new {@link FileSystemChatMemoryStore} that persists chat memories to the specified directory.
     * <p>
     * If the directory does not exist, it will be created automatically (including parent directories).
     *
     * @param directory The root directory where chat memories will be stored. Must not be null.
     * @throws UncheckedIOException if the directory cannot be created.
     * @throws IllegalArgumentException if directory is null.
     */
    public FileSystemChatMemoryStore(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Directory must not be null");
        }
        this.directory = directory.toAbsolutePath().normalize();
        createDirectoryIfNotExists(this.directory);
    }

    /**
     * Retrieves messages for a specified chat memory.
     * <p>
     * If the memory does not exist, an empty list is returned.
     *
     * @param memoryId The ID of the chat memory.
     * @return List of messages for the specified chat memory. Never null.
     * @throws UncheckedIOException if an I/O error occurs while reading the memory.
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Path file = file(memoryId);

        ReentrantLock lock = lock(file);
        lock.lock();
        try {
            if (!Files.exists(file)) {
                return Collections.emptyList();
            }
            String json = read(file);
            return ChatMessageDeserializer.messagesFromJson(json);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Updates messages for a specified chat memory.
     * <p>
     * The provided messages represent the complete current state and will overwrite any existing messages.
     * The update is performed atomically to minimize the risk of file corruption.
     *
     * @param memoryId The ID of the chat memory.
     * @param messages List of messages for the specified chat memory.
     * @throws UncheckedIOException if an I/O error occurs while writing the memory.
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        Path file = file(memoryId);
        String json = ChatMessageSerializer.messagesToJson(messages);

        ReentrantLock lock = lock(file);
        lock.lock();
        try {
            writeAtomically(file, json);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Deletes all messages for a specified chat memory.
     * <p>
     * If the memory does not exist, this method completes without error.
     *
     * @param memoryId The ID of the chat memory.
     * @throws UncheckedIOException if an I/O error occurs while deleting the memory.
     */
    @Override
    public void deleteMessages(Object memoryId) {
        Path file = file(memoryId);

        ReentrantLock lock = lock(file);
        lock.lock();
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("Failed to delete memory '%s' from file '%s'", memoryId, file), e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resolves the file path for a given memory ID.
     *
     * @param memoryId The memory ID.
     * @return The file path.
     */
    private Path file(Object memoryId) {
        String sanitizedId = sanitize(memoryId);
        return directory.resolve(sanitizedId + FILE_EXTENSION);
    }

    /**
     * Sanitizes a memory ID to produce a valid filename.
     * <p>
     * Illegal filename characters are replaced with underscore.
     *
     * @param memoryId The memory ID.
     * @return A sanitized filename-safe string.
     */
    private String sanitize(Object memoryId) {
        String filename = String.valueOf(memoryId);
        for (char illegalChar : ILLEGAL_FILENAME_CHARS) {
            filename = filename.replace(illegalChar, '_');
        }
        return filename;
    }

    /**
     * Writes content to a file atomically.
     * <p>
     * The content is first written to a temporary file, then atomically moved to the target location.
     * If atomic move is not supported, falls back to regular replacement.
     *
     * @param target The target file path.
     * @param json The JSON content to write.
     * @throws UncheckedIOException if an I/O error occurs.
     */
    private void writeAtomically(Path target, String json) {
        Path temp = null;
        try {
            // Create temporary file in the same directory to ensure atomic move is possible
            temp = Files.createTempFile(directory, TEMP_FILE_PREFIX, FILE_EXTENSION);
            Files.writeString(temp, json, UTF_8);

            // Attempt atomic move
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Fallback to regular replacement if atomic move is not supported
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null; // Successfully moved, no cleanup needed
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("Failed to write memory to file '%s'", target), e);
        } finally {
            // Clean up temporary file if it still exists (move failed)
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    /**
     * Reads the content of a file.
     *
     * @param file The file to read.
     * @return The file content, or empty string if the file no longer exists.
     * @throws UncheckedIOException if an I/O error occurs.
     */
    private String read(Path file) {
        try {
            return Files.readString(file, UTF_8);
        } catch (java.nio.file.NoSuchFileException e) {
            // File was deleted between existence check and read - treat as empty
            return "[]";
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("Failed to read memory from file '%s'", file), e);
        }
    }

    /**
     * Obtains a lock for the specified file.
     * <p>
     * Ensures thread-safe access to individual memory files.
     *
     * @param file The file path.
     * @return A reentrant lock for the file.
     */
    private ReentrantLock lock(Path file) {
        return locks.computeIfAbsent(file, ignored -> new ReentrantLock());
    }

    /**
     * Creates a directory if it does not exist.
     *
     * @param directory The directory path.
     * @throws UncheckedIOException if the directory cannot be created.
     */
    private void createDirectoryIfNotExists(Path directory) {
        if (Files.exists(directory)) {
            return;
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("Failed to create directory '%s'", directory), e);
        }
    }
}
