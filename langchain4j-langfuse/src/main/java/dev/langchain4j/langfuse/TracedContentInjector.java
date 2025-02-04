package dev.langchain4j.langfuse;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracedContentInjector implements ContentInjector {

    private static final Logger log = LoggerFactory.getLogger(TracedContentInjector.class);

    private final ContentInjector delegate;
    private final LangfuseTracer tracer;
    private final ObjectMapper objectMapper;

    public TracedContentInjector(ContentInjector delegate, LangfuseTracer tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ChatMessage inject(List<Content> contents, ChatMessage chatMessage) {
        String traceId = UUID.randomUUID().toString();
        Map<String, Object> traceInput = Map.of(
                "chat_message", chatMessage.toString(),
                "content_count", contents.size());
        log.debug("Starting content injection trace with input: {}", traceInput);

        String trace = tracer.startTrace("content-injector", traceInput, null);

        Map<String, Object> spanInput = Map.of(
                "chat_message_length", chatMessage.text().length(),
                "content_count", contents.size());

        log.debug("Starting content injection span with input: {}", spanInput);
        String span = tracer.startSpan(trace, "content-injection", spanInput, "START");

        try {
            tracer.logEvent(
                    traceId,
                    "content_injector_input",
                    Map.of(
                            "chat_message", chatMessage.toString(),
                            "contents", contents.stream().map(Content::toString).toList()));

            ChatMessage result = delegate.inject(contents, chatMessage);

            tracer.logEvent(traceId, "content_injector_output", Map.of("result", result.toString()));

            Map<String, Object> output = new HashMap<>();
            output.put("injected_chat_message_length", result.text().length());
            log.debug("Content injection successful, updating span with output: {}", output);

            tracer.updateSpan(span, output, "SUCCESS");
            tracer.endSpan(span, output, "SUCCESS");
            tracer.endTrace(trace, output, "SUCCESS");

            return result;

        } catch (Exception e) {
            Map<String, Object> errorOutput = new HashMap<>();
            errorOutput.put("error_type", e.getClass().getName());
            errorOutput.put("error_message", e.getMessage());
            errorOutput.put("stack_trace", getStackTrace(e));

            log.error("Content injection failed, updating span with error output: {}", errorOutput, e);

            tracer.updateSpan(span, errorOutput, "ERROR");
            tracer.endSpan(span, errorOutput, "ERROR");
            tracer.endTrace(trace, errorOutput, "ERROR");

            throw e;
        }
    }

    @Override
    public UserMessage inject(List<Content> contents, UserMessage userMessage) {
        log.debug("Injecting contents into UserMessage");
        return delegate.inject(contents, userMessage);
    }

    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
