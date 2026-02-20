package dev.langchain4j.skills;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessRunnerTest {

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void should_capture_stdout_on_unix() throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run("echo hello", null, 10);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stdOut()).contains("hello");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_capture_stdout_on_windows() throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run("echo hello", null, 10);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stdOut()).contains("hello");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void should_report_nonzero_exit_with_stderr_on_unix() throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run("ls /nonexistent_path_xyz", null, 10);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.exitCode()).isNotEqualTo(0);
        assertThat(result.stdErr()).isNotBlank();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_report_nonzero_exit_on_windows() throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run("dir nonexistent_path_xyz_abc", null, 10);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.exitCode()).isNotEqualTo(0);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void should_timeout_on_unix() {
        long start = System.currentTimeMillis();

        assertThatThrownBy(() -> ProcessRunner.run("sleep 60", null, 1))
                .isInstanceOf(ProcessRunner.TimeoutException.class)
                .hasMessageContaining("timed out");

        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isBetween(1_000L, 5_000L);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_timeout_on_windows() {
        long start = System.currentTimeMillis();

        assertThatThrownBy(() -> ProcessRunner.run("ping -n 62 127.0.0.1", null, 1))
                .isInstanceOf(ProcessRunner.TimeoutException.class)
                .hasMessageContaining("timed out");

        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isBetween(1_000L, 5_000L);
    }
}
