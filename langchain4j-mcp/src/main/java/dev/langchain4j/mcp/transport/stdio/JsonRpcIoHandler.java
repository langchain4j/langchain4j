package dev.langchain4j.mcp.transport.stdio;

import static dev.langchain4j.internal.Utils.getOrDefault;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRpcIoHandler implements Runnable, Closeable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(JsonRpcIoHandler.class);
    private static final Logger DEFAULT_TRAFFIC_LOG = LoggerFactory.getLogger("MCP");

    private final InputStream input;
    private final PrintStream out;
    private final boolean logEvents;
    private final Logger trafficLog;
    private final Consumer<JsonNode> messageHandler;
    private volatile boolean closed = false;

    public JsonRpcIoHandler(
            InputStream input, OutputStream output, Consumer<JsonNode> messageHandler, boolean logEvents) {
        this(input, output, messageHandler, logEvents, null);
    }

    public JsonRpcIoHandler(
            InputStream input,
            OutputStream output,
            Consumer<JsonNode> messageHandler,
            boolean logEvents,
            Logger logger) {
        this.input = input;
        this.logEvents = logEvents;
        this.messageHandler = messageHandler;
        this.out = new PrintStream(output, true);
        this.trafficLog = getOrDefault(logger, DEFAULT_TRAFFIC_LOG);
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (logEvents) {
                    trafficLog.debug("< {}", line);
                }
                try {
                    messageHandler.accept(OBJECT_MAPPER.readTree(line));
                } catch (JsonProcessingException e) {
                    log.warn("Ignoring message received because it is not valid JSON: {}", line);
                }
            }
        } catch (IOException e) {
            // If this handler was closed, it means the transport is shutting down,
            // so an IOException is expected, let's not spook the user.
            if (!closed) {
                throw new RuntimeException(e);
            }
        }
        log.debug("JsonRpcIoHandler has finished reading input stream");
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
        input.close();
        out.close();
    }
}
