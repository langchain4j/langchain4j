package dev.langchain4j.langfuse;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.langfuse.model.Generation;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TracedChatLanguageModel implements ChatLanguageModel {

    private final ChatLanguageModel delegate;
    private final LangfuseTracer tracer;
    private final String traceId;

    public TracedChatLanguageModel(ChatLanguageModel delegate, LangfuseTracer tracer, String traceId) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.traceId = traceId;
    }

    @Override
    public String generate(String prompt) {
        String spanId = tracer.startSpan(traceId, "chat_generation", Map.of("prompt", prompt), null);

        try {
            String response = delegate.generate(prompt);

            Generation params =
                    new Generation.Builder().prompt(prompt).completion(response).build();

            tracer.logGeneration(traceId, params);

            tracer.endSpan(spanId, Map.of("completion", response), "Success");

            return response;

        } catch (Exception e) {
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error_type", e.getClass().getName());
            errorData.put("error_message", e.getMessage());
            tracer.endSpan(spanId, errorData, "Error");
            throw e;
        }
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        String spanId = tracer.startSpan(traceId, "chat_generation", Map.of("messages", messages), null);

        try {
            Response<AiMessage> response = delegate.generate(messages);

            Generation params = new Generation.Builder()
                    .model(getModelName())
                    .prompt(formatMessages(messages))
                    .completion(response.content().text())
                    // .metadata("message_count", new Object(messages.size()))
                    .build();

            tracer.logGeneration(traceId, params);

            tracer.endSpan(
                    spanId,
                    Map.of(
                            "completion", response.content(),
                            "token_usage", response.tokenUsage()),
                    "Success");

            return response;

        } catch (Exception e) {
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error_type", e.getClass().getName());
            errorData.put("error_message", e.getMessage());
            tracer.endSpan(spanId, errorData, "Error");
            throw e;
        }
    }

    private String getModelName() {
        return "";
    }

    private String formatMessages(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            sb.append(message.type()).append(": ").append(message.text()).append("\n");
        }
        return sb.toString();
    }
}
