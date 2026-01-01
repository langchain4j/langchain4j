package dev.langchain4j.mcp.server.transport;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import dev.langchain4j.mcp.server.McpServer;
import dev.langchain4j.mcp.transport.stdio.JsonRpcIoHandler;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;

/**
 * WARNING: When using this transport with {@code System.out}, ensure that your application logger
 * (e.g., SLF4J, Logback) is configured to write to {@code System.err} instead of {@code System.out}.
 * Any extraneous output to stdout will corrupt the JSON-RPC protocol stream and cause the client
 * to disconnect.
 */
public class StdioMcpServerTransport implements Closeable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final McpServer server;
    private final JsonRpcIoHandler ioHandler;
    private final Thread ioThread;

    public StdioMcpServerTransport(InputStream input, OutputStream output, McpServer server) {
        this.server = ensureNotNull(server, "server");
        ensureNotNull(input, "input");
        ensureNotNull(output, "output");
        this.ioHandler = new JsonRpcIoHandler(input, output, this::handleMessage, false);
        this.ioThread = new Thread(ioHandler, "mcp-stdio-server");
        this.ioThread.setDaemon(true);
        this.ioThread.start();
    }

    /**
     * WARNING: When using this transport with {@code System.out}, ensure that your application logger
     * (e.g., SLF4J, Logback) is configured to write to {@code System.err} instead of {@code System.out}.
     * Any extraneous output to stdout will corrupt the JSON-RPC protocol stream and cause the client
     * to disconnect.
     */
    @SuppressWarnings("java:S106")
    public StdioMcpServerTransport(McpServer server) {
        this(System.in, System.out, server);
    }

    private void handleMessage(JsonNode message) {
        McpJsonRpcMessage response = server.handle(message);
        if (response == null) {
            return;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(response);
            ioHandler.submit(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        ioHandler.close();
    }
}
