package dev.langchain4j.langfuse;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.langfuse.model.Generation;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TracedChatModel implements ChatModelListener {

    private final LangfuseTracer tracer;
    private final String traceId;
    private final String modelName;

    public TracedChatModel(LangfuseTracer tracer, String traceId, String modelName) {
        this.tracer = tracer;
        this.traceId = traceId;
        this.modelName = modelName;
    }

    @Override
    public void beforeRequest(List<ChatMessage> messages) {
        String spanId = tracer.startSpan(
                traceId,
                "chat_generation",
                createInputMetadata(messages),
                "RUNNING"
        );
    }

    @Override
    public void onResponse(Response<AiMessage> response) {
        Generation generation = new Generation.Builder()
                .model(modelName)
                .prompt(formatMessages(response.inputMessages()))
                .completion(response.content().text())
                .promptTokens(response.tokenUsage().inputTokens())
                .completionTokens(response.tokenUsage().outputTokens())
                .build();

        String generationId = tracer.logGeneration(traceId, generation);

        Map<String, Object> output = new HashMap<>();
        output.put("completion", response.content().text());
        output.put("token_usage", response.tokenUsage());
        output.put("generation_id", generationId);

        tracer.endSpan(generationId, output, "SUCCESS");
    }

    @Override
    public void onError(Throwable error) {
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("error_type", error.getClass().getName());
        errorData.put("error_message", error.getMessage());
        errorData.put("stack_trace", getStackTrace(error));

        tracer.endSpan(traceId, errorData, "ERROR");
    }

    private Map<String, Object> createInputMetadata(List<ChatMessage> messages) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("model", modelName);
        metadata.put("message_count", messages.size());
        metadata.put("messages", formatMessages(messages));
        return metadata;
    }

    private String formatMessages(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            sb.append(message.type())
                    .append(": ")
                    .append(message.text())
                    .append("\n");
        }
        return sb.toString();
    }

    private String getStackTrace(Throwable error) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : error.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}

