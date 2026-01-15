package dev.langchain4j.agentskills.execution;

import dev.langchain4j.Experimental;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link ScriptExecutor} using ProcessBuilder.
 * <p>
 * This executor runs scripts in a subprocess with configurable timeout.
 * Scripts are executed using the system shell (sh on Unix, cmd on Windows).
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
public class DefaultScriptExecutor implements ScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultScriptExecutor.class);

    private final long timeoutSeconds;

    /**
     * Creates a new executor with the default timeout of 60 seconds.
     */
    public DefaultScriptExecutor() {
        this(60);
    }

    /**
     * Creates a new executor with the specified timeout.
     *
     * @param timeoutSeconds the timeout in seconds
     */
    public DefaultScriptExecutor(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public ScriptExecutionResult execute(Path workingDirectory, String command) {
        // Validate parameters
        if (workingDirectory == null) {
            throw new IllegalArgumentException("workingDirectory cannot be null");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command cannot be null or blank");
        }

        log.debug("Executing script in {}: {}", workingDirectory, command);

        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.directory(workingDirectory.toFile());
            // Merge stderr into stdout to avoid deadlock when reading streams sequentially
            processBuilder.redirectErrorStream(true);

            // Choose shell based on operating system
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("sh", "-c", command);
            }

            process = processBuilder.start();

            // Wait for process to complete or timeout
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                // Process timed out, kill it forcibly
                process.destroyForcibly();
                // Wait a bit for the process to be killed
                process.waitFor(1, TimeUnit.SECONDS);
                log.warn("Script execution timed out after {} seconds: {}", timeoutSeconds, command);
                return ScriptExecutionResult.builder()
                        .exitCode(-1)
                        .error("Script execution timed out after " + timeoutSeconds + " seconds")
                        .build();
            }

            // Process completed within timeout, read output
            String output;
            try (BufferedReader outputReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                output = outputReader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.exitValue();
            log.debug("Script completed with exit code {}", exitCode);

            return ScriptExecutionResult.builder()
                    .exitCode(exitCode)
                    .output(output)
                    .error("")
                    .build();

        } catch (IOException e) {
            log.error("IO error executing script: {}", command, e);
            return ScriptExecutionResult.builder()
                    .exitCode(-1)
                    .error("IO error: " + e.getMessage())
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Script execution interrupted: {}", command, e);
            return ScriptExecutionResult.builder()
                    .exitCode(-1)
                    .error("Execution interrupted: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Failed to execute script: {}", command, e);
            return ScriptExecutionResult.builder()
                    .exitCode(-1)
                    .error("Execution failed: " + e.getMessage())
                    .build();
        } finally {
            // Ensure process is destroyed in case of any exception
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
