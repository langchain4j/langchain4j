package dev.langchain4j.mcp.client.transport.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

class SseSubscriber implements Flow.Subscriber<String> {

    /**
     * Future for the result of the operation that this SSE subscriber was created for.
     * If this is a subsidiary SSE subscriber for the long-lived GET channel (therefore, no operation), this will be null.
     */
    private final CompletableFuture<JsonNode> future;

    private final Logger logger;
    private final boolean logResponses;
    private final McpOperationHandler operationHandler;
    private Flow.Subscription subscription;
    private final boolean subsidiary;
    private final AtomicReference<String> lastEventId;
    private final AtomicLong retryMs;
    private final Runnable onStreamEnd;
    private final AtomicBoolean transportClosed;

    /**
     * Constructor for a regular (non-subsidiary) SSE subscriber, used for POST response streams.
     */
    SseSubscriber(
            CompletableFuture<JsonNode> future,
            boolean logResponses,
            McpOperationHandler operationHandler,
            Logger logger) {
        this.future = future;
        this.logResponses = logResponses;
        this.operationHandler = operationHandler;
        this.logger = logger;
        this.subsidiary = false;
        this.lastEventId = null;
        this.retryMs = null;
        this.onStreamEnd = null;
        // in a regular subscriber, we don't really need this information that the transport is closed
        this.transportClosed = new AtomicBoolean(false);
    }

    /**
     * Constructor for a subsidiary SSE subscriber, used for the long-lived GET SSE channel.
     */
    SseSubscriber(
            boolean logResponses,
            McpOperationHandler operationHandler,
            Logger logger,
            AtomicReference<String> lastEventId,
            AtomicLong retryMs,
            Runnable onStreamEnd,
            AtomicBoolean transportClosed) {
        this.future = null;
        this.logResponses = logResponses;
        this.operationHandler = operationHandler;
        this.logger = logger;
        this.subsidiary = true;
        this.lastEventId = lastEventId;
        this.retryMs = retryMs;
        this.onStreamEnd = onStreamEnd;
        this.transportClosed = transportClosed;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(String item) {
        if (logResponses && !item.trim().isEmpty()) {
            logger.info("SSE event received: " + item);
        }
        subscription.request(1);
        if (item.startsWith("data:")) {
            try {
                operationHandler.handle(StreamableHttpMcpTransport.OBJECT_MAPPER.readTree(item.substring(5)));
            } catch (JsonProcessingException e) {
                logger.warn("Failed to parse SSE event: " + item, e);
            }
        } else if (item.startsWith("id:") && lastEventId != null) {
            lastEventId.set(item.substring(3).trim());
        } else if (item.startsWith("retry:") && retryMs != null) {
            try {
                retryMs.set(Long.parseLong(item.substring(6).trim()));
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse SSE retry value: " + item, e);
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (subsidiary && !transportClosed.get()) {
            logger.debug("Subsidiary SSE channel error", throwable);
            if (onStreamEnd != null) {
                onStreamEnd.run();
            }
        } else {
            future.completeExceptionally(throwable);
        }
    }

    @Override
    public void onComplete() {
        if (subsidiary) {
            logger.debug("Subsidiary SSE channel closed");
            if (onStreamEnd != null && !transportClosed.get()) {
                onStreamEnd.run();
            }
        } else {
            logger.debug("SSE channel closed");
        }
    }
}
