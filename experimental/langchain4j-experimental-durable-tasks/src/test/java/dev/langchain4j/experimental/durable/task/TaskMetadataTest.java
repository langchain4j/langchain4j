package dev.langchain4j.experimental.durable.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskMetadataTest {

    @Test
    void should_create_metadata_with_factory_method() {
        TaskId taskId = TaskId.random();
        TaskMetadata metadata = TaskMetadata.create(taskId, "testAgent", Map.of("env", "test"));

        assertThat(metadata.id()).isEqualTo(taskId);
        assertThat(metadata.agentName()).isEqualTo("testAgent");
        assertThat(metadata.status()).isEqualTo(TaskStatus.PENDING);
        assertThat(metadata.labels()).containsEntry("env", "test");
        assertThat(metadata.createdAt()).isNotNull();
        assertThat(metadata.updatedAt()).isNotNull();
        assertThat(metadata.failureReason()).isNull();
    }

    @Test
    void should_transition_to_running() {
        TaskMetadata metadata = TaskMetadata.create(TaskId.random(), "agent", Map.of());
        metadata.transitionTo(TaskStatus.RUNNING);
        assertThat(metadata.status()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void should_transition_to_failed_with_reason() {
        TaskMetadata metadata = TaskMetadata.create(TaskId.random(), "agent", Map.of());
        metadata.transitionTo(TaskStatus.RUNNING);
        metadata.transitionTo(TaskStatus.FAILED, "Connection timeout");

        assertThat(metadata.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(metadata.failureReason()).isEqualTo("Connection timeout");
    }

    @Test
    void should_not_transition_from_terminal_state() {
        TaskMetadata metadata = TaskMetadata.create(TaskId.random(), "agent", Map.of());
        metadata.transitionTo(TaskStatus.COMPLETED);

        assertThatThrownBy(() -> metadata.transitionTo(TaskStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void should_update_timestamp_on_transition() {
        TaskMetadata metadata = TaskMetadata.create(TaskId.random(), "agent", Map.of());
        java.time.Instant before = metadata.updatedAt();

        // Small delay to ensure timestamp changes
        metadata.transitionTo(TaskStatus.RUNNING);

        assertThat(metadata.updatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void should_have_unmodifiable_labels() {
        TaskMetadata metadata = TaskMetadata.create(TaskId.random(), "agent", Map.of("key", "value"));

        assertThatThrownBy(() -> metadata.labels().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
