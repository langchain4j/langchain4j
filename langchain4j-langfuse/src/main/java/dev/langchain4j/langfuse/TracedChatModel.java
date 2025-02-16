package dev.langchain4j.langfuse;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import java.util.Map;

public class TracedChatModel implements ChatModelListener {

    private final LangfuseTracer tracer;

    public TracedChatModel(LangfuseTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // Start a trace or span for the request
        String traceId = tracer.startTrace(
                "chat-request", Map.of("messages", requestContext.chatRequest().messages()), "START");

        // Store the traceId in the attributes map for later use
        requestContext.attributes().put("traceId", traceId);
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        // Retrieve the traceId from the attributes map
        String traceId = (String) responseContext.attributes().get("traceId");

        // Log the response and end the trace
        tracer.endTrace(
                traceId,
                Map.of(
                        "toolUse", responseContext.chatResponse().aiMessage().toolExecutionRequests(),
                        "completion", responseContext.chatResponse().aiMessage().text(),
                        "promptTokens",
                                responseContext.chatResponse().tokenUsage().inputTokenCount(),
                        "completionTokens",
                                responseContext.chatResponse().tokenUsage().outputTokenCount(),
                        "finishReason", responseContext.chatResponse().finishReason()),
                "SUCCESS");
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        // Retrieve the traceId from the attributes map
        String traceId = (String) errorContext.attributes().get("traceId");

        tracer.endTrace(
                traceId,
                Map.of(
                        "error_type", errorContext.error().getClass().getName(),
                        "error_message", errorContext.error().getMessage()),
                "ERROR");
    }
}
