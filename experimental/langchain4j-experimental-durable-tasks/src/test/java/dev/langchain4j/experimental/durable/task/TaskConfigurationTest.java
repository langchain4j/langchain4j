package dev.langchain4j.experimental.durable.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TaskConfigurationTest {

    @Test
    void should_build_with_defaults() {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("myAgent").build();

        assertThat(config.agentName()).isEqualTo("myAgent");
        assertThat(config.checkpointPolicy()).isNull(); // null = defers to service default
        assertThat(config.labels()).isEmpty();
        assertThat(config.timeout()).isNull();
    }

    @Test
    void should_build_with_custom_values() {
        java.time.Duration timeout = java.time.Duration.ofMinutes(30);
        java.util.Map<String, String> labels = java.util.Map.of("team", "ml");

        TaskConfiguration config = TaskConfiguration.builder()
                .agentName("customAgent")
                .checkpointPolicy(CheckpointPolicy.AFTER_ROOT_CALL)
                .labels(labels)
                .timeout(timeout)
                .build();

        assertThat(config.agentName()).isEqualTo("customAgent");
        assertThat(config.checkpointPolicy()).isEqualTo(CheckpointPolicy.AFTER_ROOT_CALL);
        assertThat(config.labels()).containsEntry("team", "ml");
        assertThat(config.timeout()).isEqualTo(timeout);
    }

    @Test
    void should_build_with_retry_policy() {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(5)
                .initialDelay(Duration.ofSeconds(2))
                .build();

        TaskConfiguration config = TaskConfiguration.builder()
                .agentName("retryAgent")
                .retryPolicy(retryPolicy)
                .build();

        assertThat(config.retryPolicy()).isNotNull();
        assertThat(config.retryPolicy().maxRetries()).isEqualTo(5);
        assertThat(config.retryPolicy().initialDelay()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void should_have_null_retry_policy_by_default() {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("defaultAgent").build();

        assertThat(config.retryPolicy()).isNull();
    }
}
