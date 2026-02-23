package dev.langchain4j.skills.scripts;

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
import java.util.concurrent.atomic.AtomicBoolean;

class ProcessRunner {

    static final int DEFAULT_TIMEOUT_SECONDS = 30;
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

    static Result run(String command, Path workingDirectory, int timeoutSeconds)
            throws IOException, InterruptedException {
        return run(command, workingDirectory, timeoutSeconds, DEFAULT_MAX_OUTPUT_BYTES);
    }

    static Result run(String command, Path workingDirectory, int timeoutSeconds, int maxOutputBytes)
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

        Future<String> stdOutFuture = DefaultExecutorProvider.getDefaultExecutorService() // TODO customizable
                .submit(() -> readStream(process.getInputStream(), maxOutputBytes, timedOut));
        Future<String> stdErrFuture = DefaultExecutorProvider.getDefaultExecutorService() // TODO customizable
                .submit(() -> readStream(process.getErrorStream(), maxOutputBytes, timedOut));

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
            stdOutFuture.cancel(true);
            stdErrFuture.cancel(true);

            String partialStdOut = getPartialOutput(stdOutFuture);
            String partialStdErr = getPartialOutput(stdErrFuture);
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
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            int bytesRead = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                bytesRead += line.length() + 1; // approximate
                if (bytesRead > maxBytes) {
                    sb.append("\n... [output truncated at ~").append(maxBytes / 1024).append(" KB]");
                    break;
                }
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        } catch (IOException e) {
            if (timedOut.get()) {
                // Stream closed because process was destroyed on timeout — return what we have
                return sb.toString();
            }
            throw e; // Real I/O error on the happy path — propagate
        }
        return sb.toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("win");
    }
}