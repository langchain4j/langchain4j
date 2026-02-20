package dev.langchain4j.skills;

import dev.langchain4j.internal.DefaultExecutorProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class ProcessRunner {

    static final int DEFAULT_TIMEOUT_SECONDS = 30;

    record Result(int exitCode, String stdout, String stderr) {
        boolean isSuccess() {
            return exitCode == 0;
        }
    }

    static class TimeoutException extends IOException {
        TimeoutException(String message) {
            super(message);
        }
    }

    static Result run(String command, Path workingDirectory, int timeoutSeconds)
            throws IOException, InterruptedException {

        List<String> shellCommand = isWindows()
                ? List.of("cmd", "/c", command)
                : List.of("sh", "-c", command);

        ProcessBuilder pb = new ProcessBuilder(shellCommand);
        if (workingDirectory != null) {
            pb.directory(workingDirectory.toFile());
        }

        Process process = pb.start();

        // Read stdout and stderr in separate threads to avoid pipe buffer deadlocks.
        // Uses the shared executor which prefers virtual threads (Java 21+), where
        // blocking I/O is interruptible, making cancellation reliable on all platforms.
        Future<String> stdoutFuture = DefaultExecutorProvider.getDefaultExecutorService() // TODO customizable
                .submit(() -> readStream(process.getInputStream()));
        Future<String> stderrFuture = DefaultExecutorProvider.getDefaultExecutorService() // TODO customizable
                .submit(() -> readStream(process.getErrorStream()));

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            // TODO
            // Close streams to trigger EOF/IOException in the reader threads (platform threads),
            // and cancel to interrupt them (virtual threads).
            try { process.getInputStream().close(); } catch (IOException ignored) {}
            try { process.getErrorStream().close(); } catch (IOException ignored) {}
            stdoutFuture.cancel(true);
            stderrFuture.cancel(true);
            throw new TimeoutException("Command timed out after " + timeoutSeconds + " seconds");
        }

        try {
            return new Result(process.exitValue(), stdoutFuture.get(), stderrFuture.get());
        } catch (ExecutionException e) {
            throw new IOException("Failed to read process output", e.getCause());
        }
    }

    private static String readStream(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
            return sb.toString().stripTrailing();
        } catch (IOException e) {
            // Stream was closed externally (e.g. process timed out and was destroyed)
            return "";
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("win");
    }
}
