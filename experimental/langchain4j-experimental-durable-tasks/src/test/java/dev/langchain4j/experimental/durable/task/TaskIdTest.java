package dev.langchain4j.experimental.durable.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TaskIdTest {

    @Test
    void should_create_task_id_with_value() {
        TaskId taskId = new TaskId("test-123");
        assertThat(taskId.value()).isEqualTo("test-123");
    }

    @Test
    void should_create_random_task_id() {
        TaskId taskId = TaskId.random();
        assertThat(taskId.value()).isNotBlank();
    }

    @Test
    void should_generate_unique_random_ids() {
        TaskId id1 = TaskId.random();
        TaskId id2 = TaskId.random();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void should_throw_when_value_is_null() {
        assertThatThrownBy(() -> new TaskId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_value_is_blank() {
        assertThatThrownBy(() -> new TaskId("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_have_meaningful_to_string() {
        TaskId taskId = new TaskId("abc");
        assertThat(taskId.toString()).contains("abc");
    }

    @Test
    void should_be_equal_for_same_value() {
        TaskId id1 = new TaskId("same");
        TaskId id2 = new TaskId("same");
        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }
}
