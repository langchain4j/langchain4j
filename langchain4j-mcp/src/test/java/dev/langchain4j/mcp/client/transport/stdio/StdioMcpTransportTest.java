package dev.langchain4j.mcp.client.transport.stdio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StdioMcpTransportTest {

    @Test
    void should_inherit_working_directory_by_default() throws Exception {
        ExecutorService executorService = Executors.newCachedThreadPool();
        try (StdioMcpTransport transport =
                workingDirectoryProcessBuilder(executorService).build()) {
            McpOperationHandler messageHandler = mock(McpOperationHandler.class);

            transport.start(messageHandler);

            verify(messageHandler, timeout(5_000))
                    .onMessage(Path.of("").toRealPath().toUri().toString());
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void should_use_configured_working_directory_on_start_and_restart(@TempDir Path tempDir) throws Exception {
        Path workingDirectory = Files.createDirectory(tempDir.resolve("directory with spaces"));
        ExecutorService executorService = Executors.newCachedThreadPool();
        try (StdioMcpTransport transport = workingDirectoryProcessBuilder(executorService)
                .workingDirectory(workingDirectory)
                .build()) {
            McpOperationHandler messageHandler = mock(McpOperationHandler.class);
            String expectedDirectory = workingDirectory.toRealPath().toUri().toString();

            transport.start(messageHandler);
            verify(messageHandler, timeout(5_000)).onMessage(expectedDirectory);

            transport.start(messageHandler);
            verify(messageHandler, timeout(5_000).times(2)).onMessage(expectedDirectory);
        } finally {
            executorService.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "file"})
    void should_fail_to_start_with_invalid_working_directory(String kind, @TempDir Path tempDir) throws Exception {
        Path workingDirectory = tempDir.resolve(kind);
        if (kind.equals("file")) {
            Files.createFile(workingDirectory);
        }
        ExecutorService executorService = Executors.newCachedThreadPool();
        try (StdioMcpTransport transport = workingDirectoryProcessBuilder(executorService)
                .workingDirectory(workingDirectory)
                .build()) {
            assertThatThrownBy(() -> transport.start(mock(McpOperationHandler.class)))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(IOException.class);
            assertThat(transport.getProcess()).isNull();
        } finally {
            executorService.shutdownNow();
        }
    }

    private static StdioMcpTransport.Builder workingDirectoryProcessBuilder(ExecutorService executorService)
            throws URISyntaxException {
        String javaExecutable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", javaExecutable);
        Path testClasses = Path.of(WorkingDirectoryProcess.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        return new StdioMcpTransport.Builder()
                .command(List.of(
                        java.toString(), "-cp", testClasses.toString(), WorkingDirectoryProcess.class.getName()))
                .executorService(executorService);
    }

    public static class WorkingDirectoryProcess {
        public static void main(String[] args) throws IOException {
            System.out.println(Path.of("").toRealPath().toUri());
            // Keep the process alive until the transport closes its input or destroys it.
            System.in.read();
        }
    }

    /**
     * When {@link StdioMcpTransport#start} is called again (as happens during reconnection triggered
     * by the health check), the previous subprocess must be destroyed instead of leaked.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void restarting_should_destroy_the_previous_process() throws IOException, InterruptedException {
        // Use a private executor so the background I/O handler threads spawned for the subprocess do
        // not leak into the shared default executor and interfere with other tests.
        ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            // 'cat' with no arguments reads stdin indefinitely, so the process stays alive until destroyed
            StdioMcpTransport transport = new StdioMcpTransport.Builder()
                    .command(List.of("cat"))
                    .environment(Map.of())
                    .executorService(executorService)
                    .build();

            McpOperationHandler messageHandler = mock(McpOperationHandler.class);

            transport.start(messageHandler);
            Process firstProcess = transport.getProcess();
            assertThat(firstProcess.isAlive()).isTrue();

            // Restart (simulating a reconnection)
            transport.start(messageHandler);
            Process secondProcess = transport.getProcess();

            // The previous process must have been torn down, not leaked
            assertThat(secondProcess).isNotSameAs(firstProcess);
            assertThat(firstProcess.waitFor(5, TimeUnit.SECONDS))
                    .as("the previous process should have been destroyed on restart")
                    .isTrue();
            assertThat(firstProcess.isAlive()).isFalse();
            assertThat(secondProcess.isAlive()).isTrue();

            transport.close();
            secondProcess.waitFor(5, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void retired_process_should_not_cancel_replacement_operations(@TempDir Path tempDir)
            throws IOException, InterruptedException {
        Path releaseExit = tempDir.resolve("release-exit");
        ExecutorService executorService = Executors.newCachedThreadPool();
        StdioMcpTransport transport = transportWithBlockedExit(releaseExit, executorService);
        McpOperationHandler messageHandler = mock(McpOperationHandler.class);

        try {
            transport.start(messageHandler);
            Process firstProcess = transport.getProcess();
            verify(messageHandler, timeout(5_000)).onMessage("{\"ready\":true}");

            transport.start(messageHandler);
            Process secondProcess = transport.getProcess();
            verify(messageHandler, timeout(5_000).times(2)).onMessage("{\"ready\":true}");
            clearInvocations(messageHandler);

            Files.write(releaseExit, new byte[0]);
            assertThat(firstProcess.waitFor(5, TimeUnit.SECONDS)).isTrue();

            verify(messageHandler, after(1_000).never()).cancelAllPendingOperations("Process has exited");
            assertThat(secondProcess.isAlive()).isTrue();
        } finally {
            Files.write(releaseExit, new byte[0]);
            transport.close();
            executorService.shutdownNow();
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void retiring_a_process_should_cancel_its_own_pending_operations(@TempDir Path tempDir)
            throws IOException, InterruptedException {
        Path releaseExit = tempDir.resolve("release-exit");
        ExecutorService executorService = Executors.newCachedThreadPool();
        StdioMcpTransport transport = transportWithBlockedExit(releaseExit, executorService);
        McpOperationHandler messageHandler = mock(McpOperationHandler.class);

        try {
            transport.start(messageHandler);
            Process firstProcess = transport.getProcess();
            verify(messageHandler, timeout(5_000)).onMessage("{\"ready\":true}");
            clearInvocations(messageHandler);

            transport.start(messageHandler);

            verify(messageHandler).cancelAllPendingOperations("Process has exited");
            assertThat(firstProcess.isAlive()).isTrue();
        } finally {
            Files.write(releaseExit, new byte[0]);
            transport.close();
            executorService.shutdownNow();
        }
    }

    private static StdioMcpTransport transportWithBlockedExit(Path releaseExit, ExecutorService executorService) {
        // sh runs the trap only after the current 'sleep 1' returns, which can be after JUnit has deleted the
        // @TempDir; checking that the directory still exists prevents the trap from waiting forever
        String script = "trap 'while [ ! -f \"$1\" ] && [ -d \"${1%/*}\" ]; do sleep 0.01; done; exit 0' TERM; "
                + "printf '{\"ready\":true}\\n'; while :; do sleep 1; done";
        return new StdioMcpTransport.Builder()
                .command(List.of("sh", "-c", script, "sh", releaseExit.toString()))
                .environment(Map.of())
                .executorService(executorService)
                .build();
    }
}
