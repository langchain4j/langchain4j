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
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.jlama.spi.JlamaStreamingChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.jlama.JlamaLanguageModel.toFinishReason;
import static dev.langchain4j.model.jlama.Json.fromJson;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class JlamaStreamingChatModel implements StreamingChatModel {
    private final AbstractModel model;
    private final Float temperature;
    private final Integer maxTokens;
    private final UUID id = UUID.randomUUID();

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
        JlamaModel jlamaModel = RetryUtils.withRetryMappingExceptions(() -> registry.downloadModel(modelName, Optional.ofNullable(authToken)), 2);

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
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());

        StreamingResponseHandler<AiMessage> legacyHandler = new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {
                handler.onPartialResponse(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                ChatResponse chatResponse = ChatResponse.builder()
                        .aiMessage(response.content())
                        .metadata(ChatResponseMetadata.builder()
                                .tokenUsage(response.tokenUsage())
                                .finishReason(response.finishReason())
                                .build())
                        .build();
                handler.onCompleteResponse(chatResponse);
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };

        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        if (isNullOrEmpty(toolSpecifications)) {
            generate(chatRequest.messages(), legacyHandler);
        } else {
            generate(chatRequest.messages(), toolSpecifications, legacyHandler);
        }
    }

    private void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, List.of(), handler);
    }

    private void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        if (model.promptSupport().isEmpty())
            throw new UnsupportedOperationException("This model does not support chat generation");

        PromptSupport.Builder promptBuilder = model.promptSupport().get().builder();
        for (ChatMessage message : messages) {
            switch (message.type()) {
                case SYSTEM -> promptBuilder.addSystemMessage(((SystemMessage) message).text());
                case USER -> {
                    StringBuilder finalMessage = new StringBuilder();
                    UserMessage userMessage = (UserMessage) message;
                    for (Content content : userMessage.contents()) {
                        if (content.type() != ContentType.TEXT)
                            throw new UnsupportedOperationException("Unsupported content type: " + content.type());

                        finalMessage.append(((TextContent) content).text());
                    }
                    promptBuilder.addUserMessage(finalMessage.toString());
                }
                case AI -> {
                    AiMessage aiMessage = (AiMessage) message;
                    if (aiMessage.text() != null)
                        promptBuilder.addAssistantMessage(aiMessage.text());

                    if (aiMessage.hasToolExecutionRequests())
                        for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                            ToolCall toolCall = new ToolCall(toolExecutionRequest.name(), toolExecutionRequest.id(), fromJson(toolExecutionRequest.arguments(), LinkedHashMap.class));
                            promptBuilder.addToolCall(toolCall);
                        }
                }
                case TOOL_EXECUTION_RESULT -> {
                    ToolExecutionResultMessage toolMessage = (ToolExecutionResultMessage) message;
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
        private Path modelCachePath;
        private String modelName;
        private String authToken;
        private Integer threadCount;
        private Boolean quantizeModelAtRuntime;
        private Path workingDirectory;
        private DType workingQuantizedType;
        private Float temperature;
        private Integer maxTokens;

        public JlamaStreamingChatModelBuilder() {
            // This is public, so it can be extended
        }

        public JlamaStreamingChatModelBuilder modelCachePath(Path modelCachePath) {
            this.modelCachePath = modelCachePath;
            return this;
        }

        public JlamaStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public JlamaStreamingChatModelBuilder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public JlamaStreamingChatModelBuilder threadCount(Integer threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public JlamaStreamingChatModelBuilder quantizeModelAtRuntime(Boolean quantizeModelAtRuntime) {
            this.quantizeModelAtRuntime = quantizeModelAtRuntime;
            return this;
        }

        public JlamaStreamingChatModelBuilder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public JlamaStreamingChatModelBuilder workingQuantizedType(DType workingQuantizedType) {
            this.workingQuantizedType = workingQuantizedType;
            return this;
        }

        public JlamaStreamingChatModelBuilder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public JlamaStreamingChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public JlamaStreamingChatModel build() {
            return new JlamaStreamingChatModel(this.modelCachePath, this.modelName, this.authToken, this.threadCount, this.quantizeModelAtRuntime, this.workingDirectory, this.workingQuantizedType, this.temperature, this.maxTokens);
        }

        public String toString() {
            return "JlamaStreamingChatModel.JlamaStreamingChatModelBuilder(modelCachePath=" + this.modelCachePath + ", modelName=" + this.modelName + ", authToken=" + this.authToken + ", threadCount=" + this.threadCount + ", quantizeModelAtRuntime=" + this.quantizeModelAtRuntime + ", workingDirectory=" + this.workingDirectory + ", workingQuantizedType=" + this.workingQuantizedType + ", temperature=" + this.temperature + ", maxTokens=" + this.maxTokens + ")";
        }
    }
}
