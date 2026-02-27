package dev.langchain4j.experimental.durable.store.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.internal.ObjectMapperFactory;
import dev.langchain4j.experimental.durable.store.Checkpoint;
import dev.langchain4j.experimental.durable.store.TaskExecutionStore;
import dev.langchain4j.experimental.durable.store.TaskStoreException;
import dev.langchain4j.experimental.durable.store.event.TaskEvent;
import dev.langchain4j.experimental.durable.task.TaskId;
import dev.langchain4j.experimental.durable.task.TaskMetadata;
import dev.langchain4j.experimental.durable.task.TaskStatus;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File-based implementation of {@link TaskExecutionStore}.
 *
 * <p>Persists task data to a directory structure on the local filesystem:
 * <pre>
 *   baseDir/
 *     {taskId}/
 *       metadata.json       — task metadata
 *       journal.jsonl        — append-only event journal (one JSON line per event)
 *       checkpoint.json      — most recent checkpoint
 * </pre>
 *
 * <p>Writes to metadata and checkpoint files use atomic move (write to temp file
 * then rename) to prevent corruption from crashes. Journal appends use
 * {@link StandardOpenOption#SYNC} for durability.
 *
 * <p>A per-task {@link ReentrantLock} serializes concurrent operations on the same task.
 */
@Experimental
public class FileTaskExecutionStore implements TaskExecutionStore {

    private static final Logger LOG = LoggerFactory.getLogger(FileTaskExecutionStore.class);

    private static final String METADATA_FILE = "metadata.json";
    private static final String JOURNAL_FILE = "journal.jsonl";
    private static final String CHECKPOINT_FILE = "checkpoint.json";

    private final Path baseDir;
    private final ObjectMapper objectMapper;
    private final ObjectWriter compactWriter;
    private final ConcurrentHashMap<TaskId, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    private FileTaskExecutionStore(Builder builder) {
        this.baseDir = builder.baseDir;
        this.objectMapper =
                builder.objectMapper != null ? builder.objectMapper : ObjectMapperFactory.createPrettyPrinting();
        this.compactWriter = objectMapper.writer().without(SerializationFeature.INDENT_OUTPUT);
        ensureDirectoryExists(this.baseDir);
    }

    private ReentrantLock lockFor(TaskId taskId) {
        return lockMap.computeIfAbsent(taskId, id -> new ReentrantLock());
    }

    private Path taskDir(TaskId taskId) {
        return baseDir.resolve(taskId.value());
    }

    // -- Metadata --

    @Override
    public void saveMetadata(TaskMetadata metadata) {
        ReentrantLock lock = lockFor(metadata.id());
        lock.lock();
        try {
            Path dir = taskDir(metadata.id());
            ensureDirectoryExists(dir);
            atomicWrite(dir.resolve(METADATA_FILE), objectMapper.writeValueAsBytes(metadata));
        } catch (JsonProcessingException e) {
            throw new TaskStoreException("Failed to serialize metadata for task " + metadata.id(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<TaskMetadata> loadMetadata(TaskId taskId) {
        ReentrantLock lock = lockFor(taskId);
        lock.lock();
        try {
            Path file = taskDir(taskId).resolve(METADATA_FILE);
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(file.toFile(), TaskMetadata.class));
        } catch (IOException e) {
            throw new TaskStoreException("Failed to load metadata for task " + taskId, e);
        } finally {
            lock.unlock();
        }
    }

    // -- Events --

    @Override
    public void appendEvent(TaskEvent event) {
        ReentrantLock lock = lockFor(event.taskId());
        lock.lock();
        try {
            Path dir = taskDir(event.taskId());
            ensureDirectoryExists(dir);
            Path journalFile = dir.resolve(JOURNAL_FILE);
            String line = compactWriter.writeValueAsString(event);
            try (BufferedWriter writer = Files.newBufferedWriter(
                    journalFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC)) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new TaskStoreException("Failed to append event for task " + event.taskId(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<TaskEvent> loadEvents(TaskId taskId) {
        ReentrantLock lock = lockFor(taskId);
        lock.lock();
        try {
            Path journalFile = taskDir(taskId).resolve(JOURNAL_FILE);
            if (!Files.exists(journalFile)) {
                return List.of();
            }
            List<TaskEvent> events = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(journalFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        events.add(objectMapper.readValue(trimmed, TaskEvent.class));
                    }
                }
            }
            return List.copyOf(events);
        } catch (IOException e) {
            throw new TaskStoreException("Failed to load events for task " + taskId, e);
        } finally {
            lock.unlock();
        }
    }

    // -- Checkpoint --

    @Override
    public void saveCheckpoint(Checkpoint checkpoint) {
        ReentrantLock lock = lockFor(checkpoint.taskId());
        lock.lock();
        try {
            Path dir = taskDir(checkpoint.taskId());
            ensureDirectoryExists(dir);
            atomicWrite(dir.resolve(CHECKPOINT_FILE), objectMapper.writeValueAsBytes(checkpoint));
        } catch (JsonProcessingException e) {
            throw new TaskStoreException("Failed to serialize checkpoint for task " + checkpoint.taskId(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Checkpoint> loadCheckpoint(TaskId taskId) {
        ReentrantLock lock = lockFor(taskId);
        lock.lock();
        try {
            Path file = taskDir(taskId).resolve(CHECKPOINT_FILE);
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(file.toFile(), Checkpoint.class));
        } catch (IOException e) {
            throw new TaskStoreException("Failed to load checkpoint for task " + taskId, e);
        } finally {
            lock.unlock();
        }
    }

    // -- Query --

    @Override
    public Set<TaskId> getAllTaskIds() {
        try {
            if (!Files.exists(baseDir)) {
                return Set.of();
            }
            Set<TaskId> taskIds = new HashSet<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, Files::isDirectory)) {
                for (Path dir : stream) {
                    Path metadataFile = dir.resolve(METADATA_FILE);
                    if (Files.exists(metadataFile)) {
                        taskIds.add(new TaskId(dir.getFileName().toString()));
                    }
                }
            }
            return Set.copyOf(taskIds);
        } catch (IOException e) {
            throw new TaskStoreException("Failed to list task ids", e);
        }
    }

    @Override
    public Set<TaskId> getTaskIdsByStatus(TaskStatus status) {
        return getAllTaskIds().stream()
                .filter(taskId -> {
                    Optional<TaskMetadata> metadata = loadMetadata(taskId);
                    return metadata.isPresent() && metadata.get().status() == status;
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    // -- Delete --

    @Override
    public boolean delete(TaskId taskId) {
        ReentrantLock lock = lockFor(taskId);
        lock.lock();
        try {
            Path dir = taskDir(taskId);
            if (!Files.exists(dir)) {
                return false;
            }
            deleteDirectoryRecursively(dir);
            // Do NOT remove the lock entry here — removing while holding the lock
            // allows concurrent callers to create a new lock via lockFor(), bypassing
            // serialization. The small memory cost of a stale entry is acceptable.
            return true;
        } catch (IOException e) {
            throw new TaskStoreException("Failed to delete task " + taskId, e);
        } finally {
            lock.unlock();
        }
    }

    // -- Store-atomic CAS --

    @Override
    public Optional<TaskMetadata> compareAndSetStatus(
            TaskId taskId, TaskStatus expectedStatus, TaskStatus newStatus, String failureReason) {
        ReentrantLock lock = lockFor(taskId);
        lock.lock();
        try {
            Path file = taskDir(taskId).resolve(METADATA_FILE);
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            TaskMetadata metadata = objectMapper.readValue(file.toFile(), TaskMetadata.class);
            if (!metadata.compareAndTransition(expectedStatus, newStatus, failureReason)) {
                return Optional.empty();
            }
            atomicWrite(file, objectMapper.writeValueAsBytes(metadata));
            return Optional.of(metadata);
        } catch (IOException e) {
            throw new TaskStoreException("Failed to compare-and-set status for task " + taskId, e);
        } finally {
            lock.unlock();
        }
    }

    // -- Internal helpers --

    private void atomicWrite(Path target, byte[] content) {
        try {
            Path tempFile = Files.createTempFile(target.getParent(), "tmp-", ".json");
            try {
                Files.write(tempFile, content);
                try {
                    Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    LOG.debug("Atomic move not supported, falling back to replace: {}", target);
                    Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                // Clean up temp file on failure
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException suppressed) {
                    e.addSuppressed(suppressed);
                }
                throw e;
            }
        } catch (IOException e) {
            throw new TaskStoreException("Failed to write file: " + target, e);
        }
    }

    private static void ensureDirectoryExists(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new TaskStoreException("Failed to create directory: " + dir, e);
        }
    }

    private static void deleteDirectoryRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteDirectoryRecursively(entry);
                } else {
                    Files.delete(entry);
                }
            }
        }
        Files.delete(dir);
    }

    /**
     * Returns the base directory used by this store.
     *
     * @return the base directory path
     */
    public Path baseDir() {
        return baseDir;
    }

    /**
     * Creates a new builder for {@link FileTaskExecutionStore}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link FileTaskExecutionStore}.
     */
    public static final class Builder {

        private Path baseDir;
        private ObjectMapper objectMapper;

        private Builder() {}

        /**
         * Sets the base directory for task data. Required.
         *
         * @param baseDir the base directory
         * @return this builder
         */
        public Builder baseDir(Path baseDir) {
            this.baseDir = baseDir;
            return this;
        }

        /**
         * Sets a custom {@link ObjectMapper} for JSON serialization.
         * If not set, a default mapper with {@code JavaTimeModule} and pretty-print is used.
         *
         * @param objectMapper the object mapper
         * @return this builder
         */
        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        /**
         * Builds the {@link FileTaskExecutionStore}.
         *
         * @return a new file task execution store
         * @throws IllegalArgumentException if {@code baseDir} is null
         */
        public FileTaskExecutionStore build() {
            if (baseDir == null) {
                throw new IllegalArgumentException("baseDir must not be null");
            }
            return new FileTaskExecutionStore(this);
        }
    }
}
