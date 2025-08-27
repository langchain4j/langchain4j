package dev.langchain4j.mcp.client.transport.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SseSubscriber implements Flow.Subscriber<String> {
    private final CompletableFuture<JsonNode> future;
    private final Logger log = LoggerFactory.getLogger(SseSubscriber.class);
    private final boolean logResponses;
    private final McpOperationHandler operationHandler;
    private Flow.Subscription subscription;

    SseSubscriber(CompletableFuture<JsonNode> future, boolean logResponses, McpOperationHandler operationHandler) {
        this.future = future;
        this.logResponses = logResponses;
        this.operationHandler = operationHandler;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(String item) {
        if (logResponses) {
            log.info("SSE event received: " + item);
        }
        subscription.request(1);
        if (item.startsWith("data:")) {
            try {
                operationHandler.handle(StreamableHttpMcpTransport.OBJECT_MAPPER.readTree(item.split("data:")[1]));
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse SSE event: " + item, e);
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        future.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        log.debug("SSE channel closed");
    }
}
