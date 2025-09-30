package dev.langchain4j.mcp.client.transport.stdio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ProcessIOHandler implements Runnable, Closeable {

    private final Process process;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ProcessIOHandler.class);
    private static final Logger trafficLog = LoggerFactory.getLogger("MCP");
    private final boolean logEvents;
    private final McpOperationHandler messageHandler;
    private final PrintStream out;
    private volatile boolean closed = false;

    public ProcessIOHandler(Process process, McpOperationHandler messageHandler, boolean logEvents) {
        this.process = process;
        this.logEvents = logEvents;
        this.messageHandler = messageHandler;
        this.out = new PrintStream(process.getOutputStream(), true);
    }

    @Override
    public void run() {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            try (BufferedReader reader = bufferedReader) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (logEvents) {
                        trafficLog.debug("< {}", line);
                    }
                    try {
                        messageHandler.handle(OBJECT_MAPPER.readTree(line));
                    } catch(JsonProcessingException e) {
                        log.warn("Ignoring message received from the server because it is not valid JSON: " + line);
                    }
                }
            } catch (IOException e) {
                // If this handler was closed, it means the MCP server process is shutting down,
                // so an IOException is expected, let's not spook the user.
                if (!closed) {
                    throw new RuntimeException(e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                process.getInputStream().close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        log.debug("ProcessIOHandler has finished reading output from process with PID = {}", process.pid());
    }

    public void submit(String message) throws IOException {
        if (logEvents) {
            trafficLog.debug("> {}", message);
        }
        out.println(message);
    }

    @Override
    public void close() throws IOException {
        closed = true;
        try {
            out.close();
        } finally {
            process.getOutputStream().close();
        }
    }
}
