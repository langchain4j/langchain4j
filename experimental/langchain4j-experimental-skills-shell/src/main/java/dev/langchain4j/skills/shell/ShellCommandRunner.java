package dev.langchain4j.skills.shell;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.internal.DefaultExecutorProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
        return run(
                command,
                workingDirectory,
                timeoutSeconds,
                DEFAULT_MAX_OUTPUT_BYTES,
                DefaultExecutorProvider.getDefaultExecutor());
    }

    static Result run(String command, Path workingDirectory, Integer timeoutSeconds, int maxOutputBytes)
            throws IOException, InterruptedException {
        return run(
                command,
                workingDirectory,
                timeoutSeconds,
                maxOutputBytes,
                DefaultExecutorProvider.getDefaultExecutor());
    }

    static Result run(String command, Path workingDirectory, Integer timeoutSeconds, Executor executor)
            throws IOException, InterruptedException {
        return run(command, workingDirectory, timeoutSeconds, DEFAULT_MAX_OUTPUT_BYTES, executor);
    }

    static Result run(
            String command, Path workingDirectory, Integer timeoutSeconds, int maxOutputBytes, Executor executor)
            throws IOException, InterruptedException {

        List<String> shellCommand = isWindows() ? List.of("cmd", "/c", command) : List.of("sh", "-c", command);

        ProcessBuilder pb = new ProcessBuilder(shellCommand);
        if (workingDirectory != null) {
            pb.directory(workingDirectory.toFile());
        }

        Process process = pb.start();
        try {
            AtomicBoolean timedOut = new AtomicBoolean(false);

            Future<String> stdOutFuture = CompletableFuture.supplyAsync(
                    () -> readStreamUnchecked(process.getInputStream(), maxOutputBytes, timedOut), executor);
            Future<String> stdErrFuture = CompletableFuture.supplyAsync(
                    () -> readStreamUnchecked(process.getErrorStream(), maxOutputBytes, timedOut), executor);

            timeoutSeconds = getOrDefault(timeoutSeconds, DEFAULT_TIMEOUT_SECONDS);
            if (timeoutSeconds > DEFAULT_MAX_TIMEOUT_SECONDS) {
                timeoutSeconds = DEFAULT_MAX_TIMEOUT_SECONDS;
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                timedOut.set(true);
                try {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                } catch (Exception ignored) {
                }
                try {
                    process.destroyForcibly();
                } catch (Exception ignored) {
                }
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
                        "Command timed out after " + timeoutSeconds + " seconds", partialStdOut, partialStdErr);
            }

            try {
                return new Result(process.exitValue(), stdOutFuture.get(), stdErrFuture.get());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof UncheckedIOException uncheckedIOException) {
                    cause = uncheckedIOException.getCause();
                }
                throw new IOException("Failed to read process output", cause);
            }
        } catch (TimeoutException e) {
            throw e;
        } catch (Exception e) {
            process.destroyForcibly();
            throw e;
        }
    }

    private static String getPartialOutput(Future<String> future) {
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String readStreamUnchecked(InputStream is, int maxBytes, AtomicBoolean timedOut) {
        try {
            return readStream(is, maxBytes, timedOut);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readStream(InputStream is, int maxBytes, AtomicBoolean timedOut) throws IOException {
        OutputTail tail = new OutputTail(maxBytes);
        try (Reader reader = new InputStreamReader(is)) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                tail.append(buffer, read);
            }
            tail.endOfStream();
        } catch (IOException e) {
            if (timedOut.get()) {
                // Stream closed because process was destroyed on timeout — return what we have
            } else {
                throw e; // Real I/O error on the happy path - propagate
            }
        }
        return tail.toString();
    }

    /**
     * Retains the last lines of a stream within {@code maxBytes}.
     * A line longer than the limit is cut to its last {@code maxBytes} characters while it is being read,
     * so memory stays bounded even when the output contains no line breaks.
     */
    private static final class OutputTail {

        private record Line(String text, long originalLength) {

            boolean isCut() {
                return originalLength > text.length();
            }
        }

        private final int maxBytes;
        private final ArrayDeque<Line> lines = new ArrayDeque<>();
        private final StringBuilder current = new StringBuilder();
        private long currentLength;
        private boolean afterCarriageReturn;
        private int totalLines;
        private int bytesInDeque;

        OutputTail(int maxBytes) {
            this.maxBytes = Math.max(maxBytes, 1);
        }

        void append(char[] chars, int length) {
            for (int i = 0; i < length; i++) {
                char c = chars[i];
                if (afterCarriageReturn) {
                    afterCarriageReturn = false;
                    if (c == '\n') {
                        continue; // "\r\n" terminates a single line
                    }
                }
                if (c == '\n' || c == '\r') {
                    afterCarriageReturn = c == '\r';
                    endLine();
                } else {
                    current.append(c);
                    currentLength++;
                    if (current.length() >= 2 * maxBytes) {
                        // Trim in batches so that each character is copied a bounded number of times
                        current.delete(0, current.length() - maxBytes);
                    }
                }
            }
        }

        void endOfStream() {
            // Like BufferedReader.readLine(), a final line without a terminator still counts
            if (currentLength > 0) {
                endLine();
            }
        }

        private void endLine() {
            if (current.length() > maxBytes) {
                current.delete(0, current.length() - maxBytes);
            }
            Line line = new Line(current.toString(), currentLength);
            current.setLength(0);
            currentLength = 0;
            totalLines++;
            lines.addLast(line);
            bytesInDeque += line.text().length() + 1; // approximate (newline)
            // Evict oldest lines until we are within the limit
            while (bytesInDeque > maxBytes && lines.size() > 1) {
                Line evicted = lines.removeFirst();
                bytesInDeque -= evicted.text().length() + 1;
            }
        }

        @Override
        public String toString() {
            int droppedLines = totalLines - lines.size();
            // Only the newest line can be cut: a cut line alone fills the limit and evicts everything before it
            Line cutLine = lines.isEmpty() || !lines.getLast().isCut() ? null : lines.getLast();
            StringBuilder sb = new StringBuilder();
            if (droppedLines > 0 || cutLine != null) {
                sb.append("[truncated: ");
                if (droppedLines > 0) {
                    sb.append("showing last ")
                            .append(lines.size())
                            .append(" of ")
                            .append(totalLines)
                            .append(" lines");
                }
                if (droppedLines > 0 && cutLine != null) {
                    sb.append("; ");
                }
                if (cutLine != null) {
                    sb.append("last line cut to its last ")
                            .append(cutLine.text().length())
                            .append(" of ")
                            .append(cutLine.originalLength())
                            .append(" chars");
                }
                sb.append("]\n");
            }
            for (Line line : lines) {
                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
                sb.append(line.text());
            }
            return sb.toString();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("win");
    }
}
