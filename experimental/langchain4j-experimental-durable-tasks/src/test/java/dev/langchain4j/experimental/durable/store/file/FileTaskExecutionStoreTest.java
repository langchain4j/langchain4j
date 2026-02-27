package dev.langchain4j.experimental.durable.store.file;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.experimental.durable.store.Checkpoint;
import dev.langchain4j.experimental.durable.store.event.AgentInvocationCompletedEvent;
import dev.langchain4j.experimental.durable.store.event.AgentInvocationStartedEvent;
import dev.langchain4j.experimental.durable.store.event.TaskEvent;
import dev.langchain4j.experimental.durable.store.event.TaskStartedEvent;
import dev.langchain4j.experimental.durable.task.TaskId;
import dev.langchain4j.experimental.durable.task.TaskMetadata;
import dev.langchain4j.experimental.durable.task.TaskStatus;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileTaskExecutionStoreTest {

    @TempDir
    Path tempDir;

    private FileTaskExecutionStore store;

    @BeforeEach
    void setup() {
        store = FileTaskExecutionStore.builder()
                .baseDir(tempDir.resolve("tasks"))
                .build();
    }

    @Test
    void should_save_and_load_metadata() {
        TaskId taskId = TaskId.random();
        TaskMetadata metadata = TaskMetadata.create(taskId, "agent1", Map.of("env", "test"));

        store.saveMetadata(metadata);
        Optional<TaskMetadata> loaded = store.loadMetadata(taskId);

        assertThat(loaded).isPresent();
        assertThat(loaded.get().id()).isEqualTo(taskId);
        assertThat(loaded.get().agentName()).isEqualTo("agent1");
    }

    @Test
    void should_return_empty_for_unknown_task() {
        assertThat(store.loadMetadata(new TaskId("nonexistent"))).isEmpty();
    }

    @Test
    void should_append_and_load_events() {
        TaskId taskId = TaskId.random();
        Instant now = Instant.now();

        store.appendEvent(new TaskStartedEvent(taskId, now, Map.of()));
        store.appendEvent(new AgentInvocationStartedEvent(taskId, now, "agent1", "id1", Map.of()));
        store.appendEvent(new AgentInvocationCompletedEvent(taskId, now, "agent1", "id1", "\"done\""));

        List<TaskEvent> events = store.loadEvents(taskId);
        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOf(TaskStartedEvent.class);
        assertThat(events.get(2)).isInstanceOf(AgentInvocationCompletedEvent.class);
    }

    @Test
    void should_persist_events_as_jsonl() {
        TaskId taskId = TaskId.random();
        store.appendEvent(new TaskStartedEvent(taskId, Instant.now(), Map.of()));
        store.appendEvent(new AgentInvocationStartedEvent(taskId, Instant.now(), "agent1", "id1", Map.of()));

        Path journalFile = tempDir.resolve("tasks").resolve(taskId.value()).resolve("journal.jsonl");
        assertThat(journalFile).exists();
    }

    @Test
    void should_save_and_load_checkpoint() {
        TaskId taskId = TaskId.random();
        TaskMetadata metadata = TaskMetadata.create(taskId, "agent", Map.of());
        Checkpoint checkpoint = new Checkpoint(taskId, metadata, "{\"state\": 1}", 5, Instant.now());

        store.saveCheckpoint(checkpoint);
        Optional<Checkpoint> loaded = store.loadCheckpoint(taskId);

        assertThat(loaded).isPresent();
        assertThat(loaded.get().eventCount()).isEqualTo(5);
        assertThat(loaded.get().serializedScope()).isEqualTo("{\"state\": 1}");
    }

    @Test
    void should_get_all_task_ids() {
        TaskId id1 = TaskId.random();
        TaskId id2 = TaskId.random();
        store.saveMetadata(TaskMetadata.create(id1, "a1", Map.of()));
        store.saveMetadata(TaskMetadata.create(id2, "a2", Map.of()));

        Set<TaskId> ids = store.getAllTaskIds();
        assertThat(ids).containsExactlyInAnyOrder(id1, id2);
    }

    @Test
    void should_get_task_ids_by_status() {
        TaskId runningId = TaskId.random();
        TaskId pausedId = TaskId.random();

        TaskMetadata running = TaskMetadata.create(runningId, "a", Map.of());
        running.transitionTo(TaskStatus.RUNNING);

        TaskMetadata paused = TaskMetadata.create(pausedId, "a", Map.of());
        paused.transitionTo(TaskStatus.PAUSED);

        store.saveMetadata(running);
        store.saveMetadata(paused);

        assertThat(store.getTaskIdsByStatus(TaskStatus.RUNNING)).containsExactly(runningId);
        assertThat(store.getTaskIdsByStatus(TaskStatus.PAUSED)).containsExactly(pausedId);
    }

    @Test
    void should_delete_task_data() {
        TaskId taskId = TaskId.random();
        store.saveMetadata(TaskMetadata.create(taskId, "a", Map.of()));
        store.appendEvent(new TaskStartedEvent(taskId, Instant.now(), Map.of()));
        store.saveCheckpoint(
                new Checkpoint(taskId, TaskMetadata.create(taskId, "a", Map.of()), "{}", 1, Instant.now()));

        boolean deleted = store.delete(taskId);

        assertThat(deleted).isTrue();
        assertThat(store.loadMetadata(taskId)).isEmpty();
        assertThat(store.loadEvents(taskId)).isEmpty();
        assertThat(store.loadCheckpoint(taskId)).isEmpty();

        Path taskDir = tempDir.resolve("tasks").resolve(taskId.value());
        assertThat(taskDir).doesNotExist();
    }

    @Test
    void should_return_false_when_deleting_nonexistent_task() {
        assertThat(store.delete(new TaskId("no-such-task"))).isFalse();
    }

    @Test
    void should_survive_round_trip_across_store_instances() {
        TaskId taskId = TaskId.random();
        store.saveMetadata(TaskMetadata.create(taskId, "agent", Map.of()));
        store.appendEvent(new TaskStartedEvent(taskId, Instant.now(), Map.of()));

        // Create a new store instance pointing to same directory
        FileTaskExecutionStore store2 = FileTaskExecutionStore.builder()
                .baseDir(tempDir.resolve("tasks"))
                .build();

        assertThat(store2.loadMetadata(taskId)).isPresent();
        assertThat(store2.loadEvents(taskId)).hasSize(1);
    }
}
