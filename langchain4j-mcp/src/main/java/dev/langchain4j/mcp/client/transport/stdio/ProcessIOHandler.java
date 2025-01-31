package dev.langchain4j.mcp.client.transport.stdio;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ProcessIOHandler implements Runnable {

    private final Process process;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ProcessIOHandler.class);
    private final boolean logEvents;
    private final McpOperationHandler messageHandler;

    public ProcessIOHandler(Process process, McpOperationHandler messageHandler, boolean logEvents) {
        this.process = process;
        this.logEvents = logEvents;
        this.messageHandler = messageHandler;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (logEvents) {
                    log.debug("< {}", line);
                }
                messageHandler.handle(OBJECT_MAPPER.readTree(line));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.debug("ProcessIOHandler has finished reading output from process with PID = {}", process.pid());
    }

    public void submit(String message) throws IOException {
        if (logEvents) {
            log.debug("> {}", message);
        }
        process.getOutputStream().write((message + "\n").getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().flush();
    }
}
