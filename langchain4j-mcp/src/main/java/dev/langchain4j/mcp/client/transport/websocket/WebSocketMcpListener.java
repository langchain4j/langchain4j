package dev.langchain4j.mcp.client.transport.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;

import static dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport.OBJECT_MAPPER;

public class WebSocketMcpListener implements WebSocket.Listener {

    private final McpOperationHandler operationHandler;
    private final Logger trafficLogger;
    private final boolean logResponses;
    private final Runnable onCloseCallback;
    private final Logger logger = LoggerFactory.getLogger(WebSocketMcpListener.class);
    private final Runnable onFailureCallback;


    public WebSocketMcpListener(McpOperationHandler operationHandler,
                                Logger trafficLogger,
                                boolean logResponses,
                                Runnable onCloseCallback,
                                Runnable onFailureCallback) {
        this.operationHandler = operationHandler;
        this.trafficLogger = trafficLogger;
        this.logResponses = logResponses;
        this.onCloseCallback = onCloseCallback;
        this.onFailureCallback = onFailureCallback;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        logger.debug("Websocket connection opened");
        webSocket.request(1);
    }

    private volatile StringBuilder receivedMessage = new StringBuilder();

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        receivedMessage.append(data);
        if(last) {
            String completeMessage = receivedMessage.toString();
            receivedMessage = new StringBuilder();
            if (logResponses) {
                trafficLogger.info("< " + completeMessage);
            }
            try {
                JsonNode jsonNode = OBJECT_MAPPER.readTree(completeMessage);
                operationHandler.handle(jsonNode);
            } catch (JsonProcessingException e) {
                logger.warn("Failed to parse JSON message: {}", completeMessage, e);
            }
        }
        webSocket.request(1);
        return null;
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
