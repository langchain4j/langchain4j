package dev.langchain4j.skills;

import dev.langchain4j.internal.DefaultExecutorProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.langchain4j.internal.Utils.getOrDefault;

class ShellCommandRunner {

    static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_MAX_TIMEOUT_SECONDS = 5 * 60;
    private static final int DEFAULT_MAX_OUTPUT_BYTES = 10 * 1024 * 1024; // 10 MB

    record Result(int exitCode, String stdOut, String stdErr) {
        boolean isSuccess() {
            return exitCode == 0;
        }
    }

    static class TimeoutException extends IOException {

        private final String partialStdOut;
        private final String partialStdErr;

        TimeoutException(String message, String partialStdOut, String partialStdErr) {
            super(message);
            this.partialStdOut = partialStdOut;
            this.partialStdErr = partialStdErr;
        }

        String partialStdOut() {
            return partialStdOut;
        }

        String partialStdErr() {
            return partialStdErr;
        }
    }

    static Result run(String command, Path workingDirectory, Integer timeoutSeconds)
            throws IOException, InterruptedException {
        return run(command, workingDirectory, timeoutSeconds, DEFAULT_MAX_OUTPUT_BYTES, DefaultExecutorProvider.getDefaultExecutorService());
    }

    static Result run(String command, Path workingDirectory, Integer timeoutSeconds, int maxOutputBytes)
            throws IOException, InterruptedException {
        return run(command, workingDirectory, timeoutSeconds, maxOutputBytes, DefaultExecutorProvider.getDefaultExecutorService());
    }

    static Result run(String command,
                      Path workingDirectory, Integer timeoutSeconds, ExecutorService executorService)
            throws IOException, InterruptedException {
        return run(command, workingDirectory, timeoutSeconds, DEFAULT_MAX_OUTPUT_BYTES, executorService);
    }

    static Result run(String command,
                      Path workingDirectory,
                      Integer timeoutSeconds,
                      int maxOutputBytes,
                      ExecutorService executorService)
            throws IOException, InterruptedException {

        List<String> shellCommand = isWindows()
                ? List.of("cmd", "/c", command)
                : List.of("sh", "-c", command);

        ProcessBuilder pb = new ProcessBuilder(shellCommand);
        if (workingDirectory != null) {
            pb.directory(workingDirectory.toFile());
        }

        Process process = pb.start();

        AtomicBoolean timedOut = new AtomicBoolean(false);

        Future<String> stdOutFuture = executorService.submit(() ->
                readStream(process.getInputStream(), maxOutputBytes, timedOut));
        Future<String> stdErrFuture = executorService.submit(() ->
                readStream(process.getErrorStream(), maxOutputBytes, timedOut));

        timeoutSeconds = getOrDefault(timeoutSeconds, DEFAULT_TIMEOUT_SECONDS);
        if (timeoutSeconds > DEFAULT_MAX_TIMEOUT_SECONDS) {
            timeoutSeconds = DEFAULT_MAX_TIMEOUT_SECONDS;
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        if (!finished) {
            timedOut.set(true);
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            try {
                process.getInputStream().close();
            } catch (IOException ignored) {
            }
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
            }
            try {
                process.getErrorStream().close();
            } catch (IOException ignored) {
            }
            String partialStdOut = getPartialOutput(stdOutFuture);
            String partialStdErr = getPartialOutput(stdErrFuture);
            stdOutFuture.cancel(true);
            stdErrFuture.cancel(true);
            throw new TimeoutException(
                    "Command timed out after " + timeoutSeconds + " seconds",
                    partialStdOut,
                    partialStdErr
            );
        }

        try {
            return new Result(process.exitValue(), stdOutFuture.get(), stdErrFuture.get());
        } catch (ExecutionException e) {
            throw new IOException("Failed to read process output", e.getCause());
        }
    }

    private static String getPartialOutput(Future<String> future) {
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String readStream(InputStream is, int maxBytes, AtomicBoolean timedOut) throws IOException {
        ArrayDeque<String> lines = new ArrayDeque<>();
        int totalLines = 0;
        int bytesInDeque = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalLines++;
                int lineBytes = line.length() + 1; // approximate (newline)
                lines.addLast(line);
                bytesInDeque += lineBytes;
                // Evict oldest lines until we are within the limit
                while (bytesInDeque > maxBytes && lines.size() > 1) {
                    String evicted = lines.removeFirst();
                    bytesInDeque -= evicted.length() + 1;
                }
            }
        } catch (IOException e) {
            if (timedOut.get()) {
                // Stream closed because process was destroyed on timeout — return what we have
            } else {
                throw e; // Real I/O error on the happy path - propagate
            }
        }
        int droppedLines = totalLines - lines.size();
        StringBuilder sb = new StringBuilder();
        if (droppedLines > 0) {
            sb.append("[truncated: showing last ").append(lines.size())
                    .append(" of ").append(totalLines).append(" lines]\n");
        }
        for (String line : lines) {
            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("win");
    }
}