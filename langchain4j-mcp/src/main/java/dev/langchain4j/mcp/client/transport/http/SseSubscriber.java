package dev.langchain4j.mcp.client.transport.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.slf4j.Logger;

class SseSubscriber implements Flow.Subscriber<String> {
    private final CompletableFuture<JsonNode> future;
    private final Logger logger;
    private final boolean logResponses;
    private final McpOperationHandler operationHandler;
    private Flow.Subscription subscription;

    SseSubscriber(
            CompletableFuture<JsonNode> future,
            boolean logResponses,
            McpOperationHandler operationHandler,
            Logger logger) {
        this.future = future;
        this.logResponses = logResponses;
        this.operationHandler = operationHandler;
        this.logger = logger;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(String item) {
        if (logResponses) {
            logger.info("SSE event received: " + item);
        }
        subscription.request(1);
        if (item.startsWith("data:")) {
            try {
                operationHandler.handle(StreamableHttpMcpTransport.OBJECT_MAPPER.readTree(item.substring(5)));
            } catch (JsonProcessingException e) {
                logger.warn("Failed to parse SSE event: " + item, e);
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        future.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        logger.debug("SSE channel closed");
    }
}
