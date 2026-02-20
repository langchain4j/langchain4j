package dev.langchain4j.skills;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessRunnerTest {

    // ── stdout capture ────────────────────────────────────────────────────────

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

    // ── multiline stdout ──────────────────────────────────────────────────────

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void should_capture_multiline_stdout_on_unix() throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run("printf 'line1\\nline2\\nline3'", null, 10);

        assertThat(result.stdOut()).contains("line1", "line2", "line3");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_capture_multiline_stdout_on_windows() throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run("echo line1 && echo line2 && echo line3", null, 10);

        assertThat(result.stdOut()).contains("line1", "line2", "line3");
    }

    // ── empty output ──────────────────────────────────────────────────────────

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void should_return_empty_output_when_command_produces_nothing_on_unix() throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run("true", null, 10);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stdOut()).isEmpty();
        assertThat(result.stdErr()).isEmpty();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_return_empty_output_when_command_produces_nothing_on_windows() throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run("type nul", null, 10);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stdOut()).isEmpty();
        assertThat(result.stdErr()).isEmpty();
    }

    // ── stdout + stderr concurrently ──────────────────────────────────────────

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void should_capture_stdout_and_stderr_concurrently_on_unix() throws IOException, InterruptedException {
        // Verifies concurrent reading: both streams are drained simultaneously, no deadlock
        ProcessRunner.Result result = ProcessRunner.run("echo out && echo err >&2", null, 10);

        assertThat(result.stdOut()).contains("out");
        assertThat(result.stdErr()).contains("err");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_capture_stdout_and_stderr_concurrently_on_windows() throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run("echo out && echo err 1>&2", null, 10);

        assertThat(result.stdOut()).contains("out");
        assertThat(result.stdErr()).contains("err");
    }

    // ── exit code ─────────────────────────────────────────────────────────────

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
    void should_capture_specific_exit_code_on_unix() throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run("exit 42", null, 10);

        assertThat(result.exitCode()).isEqualTo(42);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_capture_specific_exit_code_on_windows() throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run("exit /b 42", null, 10);

        assertThat(result.exitCode()).isEqualTo(42);
    }

    // ── working directory ─────────────────────────────────────────────────────

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void should_run_in_specified_working_directory_on_unix(@TempDir Path tempDir) throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run("pwd", tempDir, 10);

        assertThat(result.stdOut()).contains(tempDir.toRealPath().toString());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_run_in_specified_working_directory_on_windows(@TempDir Path tempDir) throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run("cd", tempDir, 10);

        assertThat(result.stdOut()).containsIgnoringCase(tempDir.toRealPath().toString());
    }

    // ── output truncation ─────────────────────────────────────────────────────

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void should_truncate_stdout_when_max_output_bytes_exceeded_on_unix() throws IOException, InterruptedException {
        // seq generates N lines; with a tiny cap the output must be truncated
        ProcessRunner.Result result = ProcessRunner.run("seq 1 1000", null, 10, 100);

        assertThat(result.stdOut()).contains("truncated");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_truncate_stdout_when_max_output_bytes_exceeded_on_windows() throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run("for /l %i in (1,1,1000) do @echo %i", null, 10, 100);

        assertThat(result.stdOut()).contains("truncated");
    }

    // ── timeout ───────────────────────────────────────────────────────────────

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
        // ping sends N packets with ~1s between each — works reliably in non-interactive mode
        long start = System.currentTimeMillis();

        assertThatThrownBy(() -> ProcessRunner.run("ping -n 62 127.0.0.1", null, 1))
                .isInstanceOf(ProcessRunner.TimeoutException.class)
                .hasMessageContaining("timed out");

        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isBetween(1_000L, 5_000L);
    }

    // ── partial output on timeout ─────────────────────────────────────────────

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void should_capture_partial_stdout_in_timeout_exception_on_unix() {
        // echo runs instantly before sleep, so partial output must be captured
        assertThatThrownBy(() -> ProcessRunner.run("echo partial_output && sleep 60", null, 1))
                .isInstanceOf(ProcessRunner.TimeoutException.class)
                .satisfies(e -> assertThat(((ProcessRunner.TimeoutException) e).partialStdOut())
                        .contains("partial_output"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_capture_partial_stdout_in_timeout_exception_on_windows() {
        assertThatThrownBy(() -> ProcessRunner.run("echo partial_output && ping -n 62 127.0.0.1 > nul", null, 1))
                .isInstanceOf(ProcessRunner.TimeoutException.class)
                .satisfies(e -> assertThat(((ProcessRunner.TimeoutException) e).partialStdOut())
                        .contains("partial_output"));
    }
}
