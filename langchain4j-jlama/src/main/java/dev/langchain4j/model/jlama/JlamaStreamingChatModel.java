package dev.langchain4j.model.jlama;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import com.github.tjake.jlama.safetensors.prompt.PromptSupport;
import com.github.tjake.jlama.safetensors.prompt.Tool;
import com.github.tjake.jlama.safetensors.prompt.ToolCall;
import com.github.tjake.jlama.safetensors.prompt.ToolResult;
import com.github.tjake.jlama.util.JsonSupport;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.policy.RetryUtils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.jlama.spi.JlamaStreamingChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.langchain4j.model.jlama.JlamaLanguageModel.toFinishReason;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class JlamaStreamingChatModel implements StreamingChatLanguageModel {
    private final AbstractModel model;
    private final Float temperature;
    private final Integer maxTokens;
    private final UUID id = UUID.randomUUID();

    @Builder
    public JlamaStreamingChatModel(Path modelCachePath,
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

    public static JlamaStreamingChatModelBuilder builder() {
        for (JlamaStreamingChatModelBuilderFactory factory : loadFactories(JlamaStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new JlamaStreamingChatModelBuilder();
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, List.of(), handler);
    }


    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
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

        try {
            Generator.Response r = model.generate(id, promptContext, temperature, maxTokens, (token, time) -> {
                handler.onNext(token);
            });

            if (r.finishReason == Generator.FinishReason.TOOL_CALL) {
                List<ToolExecutionRequest> toolCalls = r.toolCalls.stream().map(f -> ToolExecutionRequest.builder()
                        .name(f.getName())
                        .id(f.getId())
                        .arguments(JsonSupport.toJson(f.getParameters()))
                        .build()).toList();

                handler.onComplete(Response.from(AiMessage.from(toolCalls), new TokenUsage(r.promptTokens, r.generatedTokens), toFinishReason(r.finishReason)));
            } else {
                handler.onComplete(Response.from(AiMessage.from(r.responseText), new TokenUsage(r.promptTokens, r.generatedTokens), toFinishReason(r.finishReason)));
            }
        } catch (Throwable t) {
            handler.onError(t);
        }
    }

    public static class JlamaStreamingChatModelBuilder {
        public JlamaStreamingChatModelBuilder() {
            // This is public, so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
