package dev.langchain4j.mcp.client.transport.websocket;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;

import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;


public class WebSocketMcpListener implements WebSocket.Listener {

    private static final int MESSAGE_TOO_BIG = 1009;

    private final McpOperationHandler operationHandler;
    private final Logger trafficLogger;
    private final boolean logResponses;
    private final Runnable onCloseCallback;
    private final Logger logger = LoggerFactory.getLogger(WebSocketMcpListener.class);
    private final Runnable onFailureCallback;
    private final int maxMessageSize;


    public WebSocketMcpListener(McpOperationHandler operationHandler,
                                Logger trafficLogger,
                                boolean logResponses,
                                Runnable onCloseCallback,
                                Runnable onFailureCallback) {
        this(operationHandler, trafficLogger, logResponses, onCloseCallback, onFailureCallback,
                WebSocketMcpTransport.DEFAULT_MAX_MESSAGE_SIZE);
    }

    /**
     * Creates a listener that closes the connection when a single message received from the server
     * is larger than {@code maxMessageSize} bytes.
     *
     * @since 1.24.0
     */
    public WebSocketMcpListener(McpOperationHandler operationHandler,
                                Logger trafficLogger,
                                boolean logResponses,
                                Runnable onCloseCallback,
                                Runnable onFailureCallback,
                                int maxMessageSize) {
        this.operationHandler = operationHandler;
        this.trafficLogger = trafficLogger;
        this.logResponses = logResponses;
        this.onCloseCallback = onCloseCallback;
        this.onFailureCallback = onFailureCallback;
        this.maxMessageSize = ensureGreaterThanZero(maxMessageSize, "maxMessageSize");
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        logger.debug("Websocket connection opened");
        webSocket.request(1);
    }

    private volatile StringBuilder receivedMessage = new StringBuilder();
    private long receivedMessageBytes;

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        receivedMessageBytes += utf8Length(data);
        if (receivedMessageBytes > maxMessageSize) {
            closeBecauseMessageIsTooBig(webSocket);
            return null;
        }
        receivedMessage.append(data);
        if(last) {
            String completeMessage = receivedMessage.toString();
            receivedMessage = new StringBuilder();
            receivedMessageBytes = 0;
            if (logResponses) {
                trafficLogger.info("< " + completeMessage);
            }
            try {
                operationHandler.onMessage(completeMessage);
            } catch (RuntimeException e) {
                logger.warn("Failed to parse JSON message: {}", completeMessage, e);
            }
        }
        webSocket.request(1);
        return null;
    }

    private void closeBecauseMessageIsTooBig(WebSocket webSocket) {
        receivedMessage = new StringBuilder();
        receivedMessageBytes = 0;
        String reason = "Message from the MCP server exceeds the maximum size of " + maxMessageSize
                + " bytes; use WebSocketMcpTransport.Builder.maxMessageSize to raise the limit";
        logger.warn("Closing the WebSocket connection: {}", reason);
        operationHandler.cancelAllPendingOperations(reason);
        onCloseCallback.run();
        webSocket
                .sendClose(MESSAGE_TOO_BIG, "Message too big")
                .orTimeout(5, TimeUnit.SECONDS)
                .whenComplete((ws, error) -> webSocket.abort());
    }

    private static long utf8Length(CharSequence text) {
        long length = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) {
                length += 1;
            } else if (c < 0x800 || Character.isSurrogate(c)) {
                length += 2;
            } else {
                length += 3;
            }
        }
        return length;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        logger.debug("Websocket connection closed with status {} and reason: {}", statusCode, reason);
        // Consider all running operations failed - the websocket transport does not have recovery capabilities ATM
        operationHandler.cancelAllPendingOperations("Status " + statusCode + ", Reason: " + reason);
        onCloseCallback.run();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        logger.warn("WebSocket error", error);
        onFailureCallback.run();
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        logger.warn("Received binary data, this is not supported");
        webSocket.request(1);
        return null;
    }


}
