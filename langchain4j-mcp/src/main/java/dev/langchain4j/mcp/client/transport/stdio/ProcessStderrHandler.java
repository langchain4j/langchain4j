package dev.langchain4j.mcp.client.transport.stdio;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ProcessStderrHandler implements Runnable, Closeable {

    private final Process process;
    private static final Logger log = LoggerFactory.getLogger(ProcessStderrHandler.class);
    private volatile boolean closed = false;

    public ProcessStderrHandler(final Process process) {
        this.process = process;
    }

    @Override
    public void run() {
        try (InputStreamReader inputStreamReader = new InputStreamReader(process.getErrorStream())) {
            try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[ERROR] {}", line);
                }
            } catch (IOException e) {
                // If this handler was closed, it means the MCP server process is shutting down,
                // so an IOException is expected, let's not spook the user.
                if (!closed) {
                    throw new RuntimeException(e);
                }
            }
            log.debug("ProcessErrorPrinter has finished reading error output from process with PID = " + process.pid());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                process.getErrorStream().close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }
}
