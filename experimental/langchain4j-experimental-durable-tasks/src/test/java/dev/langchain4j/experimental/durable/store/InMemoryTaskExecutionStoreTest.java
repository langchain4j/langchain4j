package dev.langchain4j.experimental.durable.store;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.experimental.durable.store.event.AgentInvocationCompletedEvent;
import dev.langchain4j.experimental.durable.store.event.AgentInvocationStartedEvent;
import dev.langchain4j.experimental.durable.store.event.TaskCompletedEvent;
import dev.langchain4j.experimental.durable.store.event.TaskEvent;
import dev.langchain4j.experimental.durable.store.event.TaskStartedEvent;
import dev.langchain4j.experimental.durable.task.TaskId;
import dev.langchain4j.experimental.durable.task.TaskMetadata;
import dev.langchain4j.experimental.durable.task.TaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryTaskExecutionStoreTest {

    private InMemoryTaskExecutionStore store;

    @BeforeEach
    void setup() {
        store = new InMemoryTaskExecutionStore();
    }

    @Test
    void should_save_and_load_metadata() {
        TaskId taskId = TaskId.random();
        TaskMetadata metadata = TaskMetadata.create(taskId, "testAgent", Map.of());

        store.saveMetadata(metadata);
        Optional<TaskMetadata> loaded = store.loadMetadata(taskId);

        assertThat(loaded).isPresent();
        assertThat(loaded.get().id()).isEqualTo(taskId);
        assertThat(loaded.get().agentName()).isEqualTo("testAgent");
    }

    @Test
    void should_return_empty_for_unknown_task() {
        assertThat(store.loadMetadata(TaskId.random())).isEmpty();
    }

    @Test
    void should_append_and_load_events() {
        TaskId taskId = TaskId.random();
        TaskEvent event1 = new TaskStartedEvent(taskId, Instant.now(), Map.of());
        TaskEvent event2 = new AgentInvocationStartedEvent(taskId, Instant.now(), "agent1", "id1", Map.of());
        TaskEvent event3 = new AgentInvocationCompletedEvent(taskId, Instant.now(), "agent1", "id1", "\"result\"");

        store.appendEvent(event1);
        store.appendEvent(event2);
        store.appendEvent(event3);

        List<TaskEvent> events = store.loadEvents(taskId);
        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOf(TaskStartedEvent.class);
        assertThat(events.get(1)).isInstanceOf(AgentInvocationStartedEvent.class);
        assertThat(events.get(2)).isInstanceOf(AgentInvocationCompletedEvent.class);
    }

    @Test
    void should_return_empty_events_for_unknown_task() {
        assertThat(store.loadEvents(TaskId.random())).isEmpty();
    }

    @Test
    void should_save_and_load_checkpoint() {
        TaskId taskId = TaskId.random();
        TaskMetadata metadata = TaskMetadata.create(taskId, "agent", Map.of());
        Checkpoint checkpoint = new Checkpoint(taskId, metadata, "{}", 5, Instant.now());

        store.saveCheckpoint(checkpoint);
        Optional<Checkpoint> loaded = store.loadCheckpoint(taskId);

        assertThat(loaded).isPresent();
        assertThat(loaded.get().taskId()).isEqualTo(taskId);
        assertThat(loaded.get().eventCount()).isEqualTo(5);
    }

    @Test
    void should_get_all_task_ids() {
        TaskId id1 = TaskId.random();
        TaskId id2 = TaskId.random();
        store.saveMetadata(TaskMetadata.create(id1, "agent1", Map.of()));
        store.saveMetadata(TaskMetadata.create(id2, "agent2", Map.of()));

        Set<TaskId> ids = store.getAllTaskIds();
        assertThat(ids).containsExactlyInAnyOrder(id1, id2);
    }

    @Test
    void should_get_task_ids_by_status() {
        TaskId runningId = TaskId.random();
        TaskId pausedId = TaskId.random();

        TaskMetadata running = TaskMetadata.create(runningId, "agent", Map.of());
        running.transitionTo(TaskStatus.RUNNING);

        TaskMetadata paused = TaskMetadata.create(pausedId, "agent", Map.of());
        paused.transitionTo(TaskStatus.PAUSED);

        store.saveMetadata(running);
        store.saveMetadata(paused);

        assertThat(store.getTaskIdsByStatus(TaskStatus.RUNNING)).containsExactly(runningId);
        assertThat(store.getTaskIdsByStatus(TaskStatus.PAUSED)).containsExactly(pausedId);
    }

    @Test
    void should_delete_task_data() {
        TaskId taskId = TaskId.random();
        store.saveMetadata(TaskMetadata.create(taskId, "agent", Map.of()));
        store.appendEvent(new TaskStartedEvent(taskId, Instant.now(), Map.of()));
        store.saveCheckpoint(
                new Checkpoint(taskId, TaskMetadata.create(taskId, "agent", Map.of()), "{}", 1, Instant.now()));

        boolean deleted = store.delete(taskId);

        assertThat(deleted).isTrue();
        assertThat(store.loadMetadata(taskId)).isEmpty();
        assertThat(store.loadEvents(taskId)).isEmpty();
        assertThat(store.loadCheckpoint(taskId)).isEmpty();
    }

    @Test
    void should_return_false_when_deleting_nonexistent_task() {
        assertThat(store.delete(TaskId.random())).isFalse();
    }

    @Test
    void should_replace_checkpoint_on_save() {
        TaskId taskId = TaskId.random();
        TaskMetadata metadata = TaskMetadata.create(taskId, "agent", Map.of());

        store.saveCheckpoint(new Checkpoint(taskId, metadata, "{}", 3, Instant.now()));
        store.saveCheckpoint(new Checkpoint(taskId, metadata, "{}", 7, Instant.now()));

        Optional<Checkpoint> loaded = store.loadCheckpoint(taskId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().eventCount()).isEqualTo(7);
    }

    @Test
    void should_handle_completed_event_in_journal() {
        TaskId taskId = TaskId.random();
        store.appendEvent(new TaskStartedEvent(taskId, Instant.now(), Map.of()));
        store.appendEvent(new TaskCompletedEvent(taskId, Instant.now(), "\"done\""));

        List<TaskEvent> events = store.loadEvents(taskId);
        assertThat(events).hasSize(2);
        assertThat(events.get(1)).isInstanceOf(TaskCompletedEvent.class);
        assertThat(((TaskCompletedEvent) events.get(1)).serializedResult()).isEqualTo("\"done\"");
    }
}
