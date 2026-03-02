package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class RunShellCommandToolExecutorTest {

    @Test
    void test_getTimeoutSeconds() {
        String timeoutSecondsParameterName = "timeout_seconds";

        RunShellCommandToolExecutor executor = executor(1, 1);

        assertThat(executor.getTimeoutSeconds(Map.of())).isNull();
        assertThat(executor.getTimeoutSeconds(Map.of(timeoutSecondsParameterName, 1))).isEqualTo(1);
        assertThat(executor.getTimeoutSeconds(Map.of(timeoutSecondsParameterName, "1"))).isEqualTo(1);
    }

    // --- stdout truncation on success ---

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void should_not_truncate_stdout_on_success_when_within_limit_on_unix() {
        ToolExecutionResult result = executor(100, 100).executeWithContext(request("echo hello"), null);

        assertThat(result.isError()).isFalse();
        assertThat(result.resultText()).contains("hello");
        assertThat(result.resultText()).doesNotContain("[truncated:");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_not_truncate_stdout_on_success_when_within_limit_on_windows() {
        ToolExecutionResult result = executor(100, 100).executeWithContext(request("echo hello"), null);

        assertThat(result.isError()).isFalse();
        assertThat(result.resultText()).contains("hello");
        assertThat(result.resultText()).doesNotContain("[truncated:");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void should_truncate_stdout_on_success_when_exceeds_limit_on_unix() {
        // seq 1 100 produces ~292 chars; limit of 20 forces truncation
        ToolExecutionResult result = executor(20, 100).executeWithContext(request("seq 1 100"), null);

        assertThat(result.isError()).isFalse();
        assertThat(result.resultText()).contains("[truncated:");
        assertThat(result.resultText()).contains("100"); // last line is always preserved
        assertThat(result.resultText()).doesNotContain("\n1\n"); // beginning is dropped
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_truncate_stdout_on_success_when_exceeds_limit_on_windows() {
        ToolExecutionResult result = executor(20, 100)
                .executeWithContext(request("for /l %i in (1,1,100) do @echo %i"), null);

        assertThat(result.isError()).isFalse();
        assertThat(result.resultText()).contains("[truncated:");
        assertThat(result.resultText()).contains("100");
    }

    // --- stdout truncation on error ---

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void should_truncate_stdout_on_error_when_exceeds_limit_on_unix() {
        ToolExecutionResult result = executor(20, 100)
                .executeWithContext(request("seq 1 100; exit 1"), null);

        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).contains("<stdout>");
        assertThat(result.resultText()).contains("[truncated:");
        assertThat(result.resultText()).contains("100");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_truncate_stdout_on_error_when_exceeds_limit_on_windows() {
        ToolExecutionResult result = executor(20, 100)
                .executeWithContext(request("(for /l %i in (1,1,100) do @echo %i) & exit /b 1"), null);

        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).contains("<stdout>");
        assertThat(result.resultText()).contains("[truncated:");
        assertThat(result.resultText()).contains("100");
    }

    // --- stderr truncation on error ---

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void should_not_truncate_stderr_on_error_when_within_limit_on_unix() {
        ToolExecutionResult result = executor(100, 100)
                .executeWithContext(request("echo err >&2; exit 1"), null);

        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).contains("<stderr>");
        assertThat(result.resultText()).contains("err");
        assertThat(result.resultText()).doesNotContain("[truncated:");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_not_truncate_stderr_on_error_when_within_limit_on_windows() {
        ToolExecutionResult result = executor(100, 100)
                .executeWithContext(request("echo err 1>&2 & exit /b 1"), null);

        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).contains("<stderr>");
        assertThat(result.resultText()).contains("err");
        assertThat(result.resultText()).doesNotContain("[truncated:");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void should_truncate_stderr_on_error_when_exceeds_limit_on_unix() {
        ToolExecutionResult result = executor(100, 20)
                .executeWithContext(request("seq 1 100 >&2; exit 1"), null);

        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).contains("<stderr>");
        assertThat(result.resultText()).contains("[truncated:");
        assertThat(result.resultText()).contains("100");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_truncate_stderr_on_error_when_exceeds_limit_on_windows() {
        ToolExecutionResult result = executor(100, 20)
                .executeWithContext(request("(for /l %i in (1,1,100) do @echo %i 1>&2) & exit /b 1"), null);

        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).contains("<stderr>");
        assertThat(result.resultText()).contains("[truncated:");
        assertThat(result.resultText()).contains("100");
    }

    // --- helpers ---

    private RunShellCommandToolExecutor executor(int maxStdOutChars, int maxStdErrChars) {
        return new RunShellCommandToolExecutor(
                RunShellCommandToolConfig.builder()
                        .maxStdOutChars(maxStdOutChars)
                        .maxStdErrChars(maxStdErrChars)
                        .build(),
                Map.of(),
                Executors.newFixedThreadPool(2),
                false
        );
    }

    private ToolExecutionRequest request(String command) {
        return ToolExecutionRequest.builder()
                .id("1")
                .name("run_shell_command")
                .arguments("{\"command\": \"" + command + "\"}")
                .build();
    }
}