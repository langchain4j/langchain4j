package dev.langchain4j.langfuse;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracedChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(TracedChatMemory.class);
    private final ChatMemory delegate;
    private final LangfuseTracer tracer;
    private Object memoryId;

    public TracedChatMemory(ChatMemory delegate, LangfuseTracer tracer, Object memoryId) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.memoryId = memoryId;
        log.debug("TracedChatMemory initialized with memoryId: {}", memoryId);
    }

    @Override
    public List<ChatMessage> messages() {
        var trace = tracer.startTrace("chat-memory-messages", Map.of("memoryId", memoryId), null);
        var span = tracer.startSpan(trace, "chat-memory-messages", Map.of("memoryId", memoryId), null);
        log.debug("Started trace: {} and span: {} for messages() with memoryId: {}", trace, span, memoryId);

        try {
            List<ChatMessage> messages = delegate.messages();
            log.debug("Retrieved messages: {} from delegate", messages);
            tracer.updateSpan(span, Map.of("messages", messages), "SUCCESS");
            tracer.endSpan(span, Map.of("messages", messages), "SUCCESS");
            tracer.endTrace(trace, Map.of("messages", messages), "SUCCESS");
            log.debug("Ended trace: {} and span: {} for messages()", trace, span);
            return messages;

        } catch (Throwable e) {
            log.error("Error retrieving messages from chat memory", e);
            tracer.endSpan(span, Map.of("error", e), "ERROR");
            tracer.endTrace(trace, Map.of("error", e), "ERROR");
            throw e;
        }
    }

    @Override
    public void add(ChatMessage message) {
        var trace = tracer.startTrace("chat-memory-add", Map.of("message", message, "memoryId", memoryId), null);
        var span = tracer.startSpan(trace, "chat-memory-add", Map.of("message", message, "memoryId", memoryId), null);
        log.debug(
                "Started trace: {} and span: {} for add() with message: {} and memoryId: {}",
                trace,
                span,
                message,
                memoryId);

        try {
            log.debug("Adding message: {} to chat memory", message);
            delegate.add(message);
            tracer.updateSpan(span, Map.of("message", message), "SUCCESS");
            tracer.endSpan(span, Map.of("message", message), "SUCCESS");
            tracer.endTrace(trace, Map.of("message", message), "SUCCESS");
            log.debug("Ended trace: {} and span: {} for add()", trace, span);
        } catch (Throwable e) {
            log.error("Error adding message to chat memory", e);
            tracer.endSpan(span, Map.of("error", e), "ERROR");
            tracer.endTrace(trace, Map.of("error", e), "ERROR");
            throw e;
        }
    }

    @Override
    public void clear() {
        var trace = tracer.startTrace("chat-memory-clear", Map.of("memoryId", memoryId), null);
        var span = tracer.startSpan(trace, "chat-memory-clear", Map.of("memoryId", memoryId), null);
        log.debug("Started trace: {} and span: {} for clear() with memoryId: {}", trace, span, memoryId);

        try {
            log.debug("Clearing chat memory");
            delegate.clear();
            tracer.updateSpan(span, Map.of(), "SUCCESS");
            tracer.endSpan(span, Map.of(), "SUCCESS");
            tracer.endTrace(trace, Map.of(), "SUCCESS");
            log.debug("Ended trace: {} and span: {} for clear()", trace, span);
        } catch (Throwable e) {
            log.error("Error clearing chat memory", e);
            tracer.endSpan(span, Map.of("error", e), "ERROR");
            tracer.endTrace(trace, Map.of("error", e), "ERROR");
            throw e;
        }
    }

    @Override
    public Object id() {
        return delegate.id();
    }
}
