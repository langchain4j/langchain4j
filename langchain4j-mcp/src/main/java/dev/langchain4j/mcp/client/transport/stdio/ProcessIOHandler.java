package dev.langchain4j.mcp.client.transport.stdio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ProcessIOHandler implements Runnable {

    private final Process process;
    private final Map<Long, CompletableFuture<JsonNode>> pendingOperations;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ProcessIOHandler.class);
    private final boolean logEvents;

    public ProcessIOHandler(
            Process process, Map<Long, CompletableFuture<JsonNode>> pendingOperations, boolean logEvents) {
        this.process = process;
        this.pendingOperations = pendingOperations;
        this.logEvents = logEvents;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (logEvents) {
                    log.debug("< {}", line);
                }
                try {
                    JsonNode message = OBJECT_MAPPER.readValue(line, JsonNode.class);
                    long messageId = message.get("id").asLong();
                    CompletableFuture<JsonNode> op = pendingOperations.remove(messageId);
                    if (op != null) {
                        op.complete(message);
                    } else {
                        log.warn("Received response for unknown message id: {}", messageId);
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse response data", e);
                }
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
