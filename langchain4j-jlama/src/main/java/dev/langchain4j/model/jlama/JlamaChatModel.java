package dev.langchain4j.model.jlama;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.*;
import com.github.tjake.jlama.util.JsonSupport;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.policy.RetryUtils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.jlama.spi.JlamaChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.nio.file.Path;
import java.util.*;

import static dev.langchain4j.model.jlama.JlamaLanguageModel.toFinishReason;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class JlamaChatModel implements ChatLanguageModel {
    private final AbstractModel model;
    private final Float temperature;
    private final Integer maxTokens;

    @Builder
    public JlamaChatModel(Path modelCachePath,
                          String modelName,
                          String authToken,
                          Integer threadCount,
                          Boolean quantizeModelAtRuntime,
                          Path workingDirectory,
                          DType workingQuantizedType,
                          Float temperature,
                          Integer maxTokens) {
        JlamaModelRegistry registry = JlamaModelRegistry.getOrCreate(modelCachePath);
        JlamaModel jlamaModel = RetryUtils.withRetry(() -> registry.downloadModel(modelName, Optional.ofNullable(authToken)), 3);

        JlamaModel.Loader loader = jlamaModel.loader();
        if (quantizeModelAtRuntime != null && quantizeModelAtRuntime)
            loader = loader.quantized();

        if (workingQuantizedType != null)
            loader = loader.workingQuantizationType(workingQuantizedType);

        if (threadCount != null)
            loader = loader.threadCount(threadCount);

        if (workingDirectory != null)
            loader = loader.workingDirectory(workingDirectory);

        this.model = loader.load();
        this.temperature = temperature == null ? 0.3f : temperature;
        this.maxTokens = maxTokens == null ? model.getConfig().contextLength : maxTokens;
    }

    public static JlamaChatModelBuilder builder() {
        for (JlamaChatModelBuilderFactory factory : loadFactories(JlamaChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new JlamaChatModelBuilder();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
       return generate(messages, List.of());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        if (model.promptSupport().isEmpty())
            throw new UnsupportedOperationException("This model does not support chat generation");

        PromptSupport.Builder promptBuilder = model.promptSupport().get().builder();

        for (ChatMessage message : messages) {
            switch (message.type()) {
                case SYSTEM -> promptBuilder.addSystemMessage(((SystemMessage)message).text());
                case USER -> {
                    StringBuilder finalMessage = new StringBuilder();
                    UserMessage userMessage = (UserMessage)message;
                    for (Content content : userMessage.contents()) {
                        if (content.type() != ContentType.TEXT)
                            throw new UnsupportedOperationException("Unsupported content type: " + content.type());

                        finalMessage.append(((TextContent)content).text());
                    }
                    promptBuilder.addUserMessage(finalMessage.toString());
                }
                case AI -> {
                    AiMessage aiMessage = (AiMessage) message;
                    if (aiMessage.text() != null)
                        promptBuilder.addAssistantMessage(aiMessage.text());

                    if (aiMessage.hasToolExecutionRequests())
                        for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                            ToolCall toolCall = new ToolCall(toolExecutionRequest.name(), toolExecutionRequest.id(), Json.fromJson(toolExecutionRequest.arguments(), LinkedHashMap.class));
                            promptBuilder.addToolCall(toolCall);
                        }
                }
                case TOOL_EXECUTION_RESULT -> {
                    ToolExecutionResultMessage toolMessage = (ToolExecutionResultMessage)message;
                    ToolResult result = ToolResult.from(toolMessage.toolName(), toolMessage.id(), toolMessage.text());
                    promptBuilder.addToolResult(result);
                }
                default -> throw new IllegalArgumentException("Unsupported message type: " + message.type());
            }
        }

        List<Tool> tools = toolSpecifications.stream().map(JlamaModel::toTool).toList();

        PromptContext promptContext = tools.isEmpty() ? promptBuilder.build() : promptBuilder.build(tools);
        Generator.Response r = model.generate(UUID.randomUUID(), promptContext, temperature, maxTokens, (token, time) -> {});

        if (r.finishReason == Generator.FinishReason.TOOL_CALL) {
            List<ToolExecutionRequest> toolCalls = r.toolCalls.stream().map(f -> ToolExecutionRequest.builder()
                    .name(f.getName())
                    .id(f.getId())
                    .arguments(JsonSupport.toJson(f.getParameters()))
                    .build()).toList();

            return Response.from(AiMessage.from(toolCalls), new TokenUsage(r.promptTokens, r.generatedTokens), toFinishReason(r.finishReason));
        }

        return Response.from(AiMessage.from(r.responseText), new TokenUsage(r.promptTokens, r.generatedTokens), toFinishReason(r.finishReason));
    }


    public static class JlamaChatModelBuilder {
        public JlamaChatModelBuilder() {
            // This is public, so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
