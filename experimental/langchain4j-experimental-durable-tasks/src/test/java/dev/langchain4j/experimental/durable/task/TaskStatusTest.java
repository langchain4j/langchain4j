package dev.langchain4j.experimental.durable.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskStatusTest {

    @Test
    void should_identify_terminal_states() {
        assertThat(TaskStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(TaskStatus.FAILED.isTerminal()).isTrue();
        assertThat(TaskStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    void should_identify_non_terminal_states() {
        assertThat(TaskStatus.PENDING.isTerminal()).isFalse();
        assertThat(TaskStatus.RUNNING.isTerminal()).isFalse();
        assertThat(TaskStatus.PAUSED.isTerminal()).isFalse();
        assertThat(TaskStatus.RETRYING.isTerminal()).isFalse();
    }
}
