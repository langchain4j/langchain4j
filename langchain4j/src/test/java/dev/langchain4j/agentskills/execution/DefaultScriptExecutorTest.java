package dev.langchain4j.agentskills.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link DefaultScriptExecutor}.
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 */
class DefaultScriptExecutorTest {

    @Test
    void should_execute_simple_command_successfully(@TempDir Path tempDir) {
        // given
        DefaultScriptExecutor executor = new DefaultScriptExecutor(60);
        String command = "echo Hello";

        // when
        ScriptExecutionResult result = executor.execute(tempDir, command);

        // then
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.output()).contains("Hello");
        assertThat(result.error()).isEmpty();
    }

    @Test
    void should_return_non_zero_exit_code_for_failing_command(@TempDir Path tempDir) {
        // given
        DefaultScriptExecutor executor = new DefaultScriptExecutor(60);
        String command = isWindows() ? "exit 1" : "sh -c 'exit 1'";

        // when
        ScriptExecutionResult result = executor.execute(tempDir, command);

        // then
        assertThat(result.exitCode()).isNotEqualTo(0);
    }

    @Test
    void should_set_working_directory_correctly(@TempDir Path tempDir) {
        // given
        DefaultScriptExecutor executor = new DefaultScriptExecutor(60);
        String command = isWindows() ? "cd" : "pwd";

        // when
        ScriptExecutionResult result = executor.execute(tempDir, command);

        // then
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.output()).contains(tempDir.toString());
    }

    @Test
    void should_throw_exception_when_command_is_null(@TempDir Path tempDir) {
        // given
        DefaultScriptExecutor executor = new DefaultScriptExecutor(60);

        // when-then
        assertThatThrownBy(() -> executor.execute(tempDir, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command");
    }

    @Test
    void should_throw_exception_when_command_is_blank(@TempDir Path tempDir) {
        // given
        DefaultScriptExecutor executor = new DefaultScriptExecutor(60);

        // when-then
        assertThatThrownBy(() -> executor.execute(tempDir, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command");
    }

    @Test
    void should_throw_exception_when_working_directory_is_null() {
        // given
        DefaultScriptExecutor executor = new DefaultScriptExecutor(60);

        // when-then
        assertThatThrownBy(() -> executor.execute(null, "echo test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workingDirectory");
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void should_execute_python_script(@TempDir Path tempDir) throws IOException {
        // given
        Path scriptsDir = tempDir.resolve("scripts");
        Files.createDirectories(scriptsDir);

        String pythonScript =
                """
                import sys
                print("Hello from Python")
                sys.exit(0)
                """;

        Files.writeString(scriptsDir.resolve("test.py"), pythonScript);

        DefaultScriptExecutor executor = new DefaultScriptExecutor(60);
        String command = "python3 scripts/test.py";

        // when
        ScriptExecutionResult result = executor.execute(tempDir, command);

        // then
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.output()).contains("Hello from Python");
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void should_handle_large_output(@TempDir Path tempDir) {
        // given
        DefaultScriptExecutor executor = new DefaultScriptExecutor(60);
        String command = "for i in {1..1000}; do echo Line $i; done";

        // when
        ScriptExecutionResult result = executor.execute(tempDir, command);

        // then
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.output()).contains("Line 1");
        assertThat(result.output()).contains("Line 1000");
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void should_timeout_for_long_running_command(@TempDir Path tempDir) {
        // given
        DefaultScriptExecutor executor = new DefaultScriptExecutor(2);
        String command = "sleep 10";

        long startTime = System.currentTimeMillis();

        // when
        ScriptExecutionResult result = executor.execute(tempDir, command);

        long elapsedTime = System.currentTimeMillis() - startTime;

        // then: should return within timeout period (2s + tolerance)
        assertThat(elapsedTime).isLessThan(4000);
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(result.error()).contains("timed out");
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void should_capture_stderr(@TempDir Path tempDir) {
        // given
        DefaultScriptExecutor executor = new DefaultScriptExecutor(60);
        String command = "sh -c 'echo error message >&2'";

        // when
        ScriptExecutionResult result = executor.execute(tempDir, command);

        // then: stderr should be captured in output (redirectErrorStream is true)
        assertThat(result.output()).contains("error message");
    }

    @Test
    void should_handle_non_existent_command(@TempDir Path tempDir) {
        // given
        DefaultScriptExecutor executor = new DefaultScriptExecutor(60);
        String command = "this_command_does_not_exist_12345";

        // when
        ScriptExecutionResult result = executor.execute(tempDir, command);

        // then
        assertThat(result.exitCode()).isNotEqualTo(0);
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void should_execute_relative_path_script(@TempDir Path tempDir) throws IOException {
        // given
        Path scriptsDir = tempDir.resolve("scripts");
        Files.createDirectories(scriptsDir);
        Path scriptFile = scriptsDir.resolve("test.sh");
        Files.writeString(scriptFile, "#!/bin/bash\necho Success\n");
        scriptFile.toFile().setExecutable(true);

        DefaultScriptExecutor executor = new DefaultScriptExecutor(60);
        String command = "bash scripts/test.sh";

        // when
        ScriptExecutionResult result = executor.execute(tempDir, command);

        // then
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.output()).contains("Success");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void should_work_on_windows(@TempDir Path tempDir) {
        // given
        DefaultScriptExecutor executor = new DefaultScriptExecutor(60);
        String command = "echo Windows Test";

        // when
        ScriptExecutionResult result = executor.execute(tempDir, command);

        // then
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.output()).contains("Windows Test");
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
