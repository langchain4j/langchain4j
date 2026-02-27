package dev.langchain4j.skills;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class RunShellCommandToolExecutorTest {

    @Test
    void test_getTimeoutSeconds() {
        // given
        String timeoutSecondsParameterName = "timeout_seconds";

        RunShellCommandToolExecutor executor = new RunShellCommandToolExecutor(
                Map.of(),
                "does not matter",
                "does not matter",
                timeoutSecondsParameterName,
                Executors.newSingleThreadExecutor(),
                false
        );

        assertThat(executor.getTimeoutSeconds(Map.of())).isNull();
        assertThat(executor.getTimeoutSeconds(Map.of(timeoutSecondsParameterName, 1))).isEqualTo(1);
        assertThat(executor.getTimeoutSeconds(Map.of(timeoutSecondsParameterName, "1"))).isEqualTo(1);
    }
}