package dev.langchain4j.mcp.transport.stdio;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Internal;
import dev.langchain4j.mcp.client.logging.McpLoggers;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
public class JsonRpcIoHandler implements Runnable, Closeable {

    private static final Logger log = LoggerFactory.getLogger(JsonRpcIoHandler.class);

    private final InputStream input;
    private final PrintStream out;
    private final boolean logEvents;
    private final Logger trafficLog;
    private final Consumer<String> messageHandler;
    private volatile boolean closed = false;

    public JsonRpcIoHandler(
            InputStream input, OutputStream output, Consumer<String> messageHandler, boolean logEvents) {
        this(input, output, messageHandler, logEvents, null);
    }

    public JsonRpcIoHandler(
            InputStream input, OutputStream output, Consumer<String> messageHandler, boolean logEvents, Logger logger) {
        this.input = input;
        this.logEvents = logEvents;
        this.messageHandler = messageHandler;
        this.out = new PrintStream(output, true, StandardCharsets.UTF_8);
        this.trafficLog = getOrDefault(logger, McpLoggers.traffic());
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (logEvents) {
                    trafficLog.debug("< {}", line);
                }
                try {
                    messageHandler.accept(line);
                } catch (RuntimeException e) {
                    // one bad message must not end the read loop and strand the subprocess
                    log.warn("Ignoring message that could not be handled: {}", line, e);
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
        out.close();
        input.close();
    }
}
