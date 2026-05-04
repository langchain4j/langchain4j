package dev.langchain4j.model.deliverance;

import com.codahale.metrics.MetricRegistry;
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
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.teknek.deliverance.DType;
import io.teknek.deliverance.generator.GeneratorParameters;
import io.teknek.deliverance.math.WrappedForkJoinPool;
import io.teknek.deliverance.model.AbstractModel;
import io.teknek.deliverance.model.AutoModelForCausaLm;
import io.teknek.deliverance.model.ModelSupport;
import io.teknek.deliverance.safetensors.fetch.ModelFetcher;
import io.teknek.deliverance.safetensors.prompt.Function;
import io.teknek.deliverance.safetensors.prompt.PromptContext;
import io.teknek.deliverance.safetensors.prompt.PromptSupport;
import io.teknek.deliverance.safetensors.prompt.Tool;
import io.teknek.deliverance.safetensors.prompt.ToolCall;
import io.teknek.deliverance.safetensors.prompt.ToolResult;
import io.teknek.deliverance.tensor.ArrayQueueTensorAllocator;
import io.teknek.deliverance.tensor.KvBufferCacheSettings;
import io.teknek.deliverance.tensor.TensorAllocator;
import io.teknek.deliverance.tensor.operations.ConfigurableTensorProvider;


import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

class DeliveranceModelSupport {

    private DeliveranceModelSupport() {
    }

    static AutoModelForCausaLm.Builder newModelBuilder(Path modelCachePath, String modelName, String authToken) {
        return AutoModelForCausaLm.newBuilder(new ConfiguredModelFetcher(modelCachePath, modelName, authToken));
    }

    static AbstractModel loadGenerationModel(AutoModelForCausaLm.Builder modelBuilder) {
        return modelBuilder.build();
    }


    static AbstractModel loadEmbeddingModel(DeliveranceEmbeddingModels.Builder modelBuilder) {

        ConfiguredModelFetcher fetcher = new ConfiguredModelFetcher(
                modelBuilder.modelCachePath(),
                modelBuilder.modelName(),
                modelBuilder.authToken()
        );
        File modelRoot = fetcher.maybeDownload();


        MetricRegistry metricRegistry = new MetricRegistry();
        TensorAllocator allocator = new ArrayQueueTensorAllocator(metricRegistry);
        WrappedForkJoinPool pool = modelBuilder.threadCount() == null
                ? new WrappedForkJoinPool(WrappedForkJoinPool.autoSizeByCores())
                : new WrappedForkJoinPool(new ForkJoinPool(modelBuilder.threadCount()));

        io.teknek.deliverance.tensor.operations.ConfigurableTensorProvider provider  = new ConfigurableTensorProvider(allocator, pool);

        return ModelSupport.loadEmbeddingModel(
                modelRoot,
                modelBuilder.workingMemoryType() == null ? DType.F32 : modelBuilder.workingMemoryType(),
                modelBuilder.workingQuantizedType() == null ? DType.F32 : modelBuilder.workingQuantizedType(),
                provider,
                metricRegistry,
                allocator,
                new KvBufferCacheSettings(true)
        );
    }


    static String modelName(DeliveranceEmbeddingModels.Builder modelBuilder) {
        return modelBuilder.modelName();
    }

    static PromptContext toPromptContext(AbstractModel model,
                                         List<ChatMessage> messages,
                                         List<ToolSpecification> toolSpecifications) {

        PromptSupport promptSupport = model.promptSupport()
                .orElseThrow(() -> new UnsupportedOperationException("This model does not support chat generation"));

        PromptSupport.Builder promptBuilder = promptSupport.builder();

        for (ChatMessage message : messages) {
            switch (message.type()) {
                case SYSTEM -> promptBuilder.addSystemMessage(((SystemMessage) message).text());
                case USER -> {
                    StringBuilder finalMessage = new StringBuilder();
                    UserMessage userMessage = (UserMessage) message;
                    for (Content content : userMessage.contents()) {
                        if (content.type() != ContentType.TEXT) {
                            throw new UnsupportedOperationException("Unsupported content type: " + content.type());
                        }
                        finalMessage.append(((TextContent) content).text());
                    }
                    promptBuilder.addUserMessage(finalMessage.toString());
                }
                case AI -> {
                    AiMessage aiMessage = (AiMessage) message;
                    if (aiMessage.text() != null) {
                        promptBuilder.addAssistantMessage(aiMessage.text());
                    }
                    if (aiMessage.hasToolExecutionRequests()) {
                        for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                            ToolCall toolCall = new ToolCall(
                                    toolExecutionRequest.name(),
                                    toolExecutionRequest.id(),
                                    Json.fromJson(toolExecutionRequest.arguments(), LinkedHashMap.class)
                            );
                            promptBuilder.addToolCall(toolCall);
                        }
                    }
                }
                case TOOL_EXECUTION_RESULT -> {
                    ToolExecutionResultMessage toolMessage = (ToolExecutionResultMessage) message;
                    if (!toolMessage.hasSingleText()) {
                        throw new UnsupportedFeatureException(
                                "Deliverance does not support non-text content in tool results. Only text content is supported."
                        );
                    }
                    promptBuilder.addToolResult(ToolResult.from(toolMessage.toolName(), toolMessage.id(), toolMessage.text()));
                }
                default -> throw new IllegalArgumentException("Unsupported message type: " + message.type());
            }
        }

        List<Tool> tools = isNullOrEmpty(toolSpecifications)
                ? List.of()
                : toolSpecifications.stream().map(DeliveranceModelSupport::toTool).toList();

        return tools.isEmpty() ? promptBuilder.build() : promptBuilder.build(tools);
    }

    static Tool toTool(ToolSpecification toolSpecification) {
        Function.Builder builder = Function.builder().name(toolSpecification.name());

        if (toolSpecification.description() != null) {
            builder.description(toolSpecification.description());
        }

        if (toolSpecification.parameters() != null) {
            List<String> required = toolSpecification.parameters().required();
            for (Map.Entry<String, dev.langchain4j.model.chat.request.json.JsonSchemaElement> entry : toolSpecification.parameters().properties().entrySet()) {
                boolean isRequired = required != null && required.contains(entry.getKey());
                builder.addParameter(entry.getKey(), JsonSchemaElementUtils.toMap(entry.getValue()), isRequired);
            }
        }

        return Tool.from(builder.build());
    }

    static GeneratorParameters copy(GeneratorParameters source) {
        GeneratorParameters generatorParameters = new GeneratorParameters();

        source.temperature.ifPresent(generatorParameters::withTemperature);
        source.ntokens.ifPresent(generatorParameters::withNtokens);
        source.seed.ifPresent(generatorParameters::withSeed);
        source.stopWords.ifPresent(generatorParameters::withStopWords);
        source.includeStopStrInOutput.ifPresent(generatorParameters::withIncludeStopStrInOutput);
        source.guidedChoice.ifPresent(generatorParameters::withGuidedChoice);
        source.maxTokens.ifPresent(generatorParameters::withMaxTokens);
        source.logProbs.ifPresent(generatorParameters::withLogProbs);
        source.topLogProbs.ifPresent(generatorParameters::withTopLogProbs);
        source.xtcThreshold.ifPresent(generatorParameters::withXtcThreshold);
        source.xtcProbability.ifPresent(generatorParameters::withXtcProbability);
        source.topK.ifPresent(generatorParameters::withTopK);
        source.topP.ifPresent(generatorParameters::withTopP);

        return generatorParameters;
    }

    static DeliveranceChatRequestParameters toDeliveranceChatRequestParameters(ChatRequestParameters parameters) {
        return DeliveranceChatRequestParameters.builder()
                .overrideWith(parameters)
                .build();
    }

    static GeneratorParameters toGeneratorParameters(DeliveranceChatRequestParameters parameters) {
        GeneratorParameters generatorParameters = new GeneratorParameters();

        if (parameters.temperature() != null) {
            generatorParameters.withTemperature(parameters.temperature().floatValue());
        }
        if (parameters.ntokens() != null) {
            generatorParameters.withNtokens(parameters.ntokens());
        }
        if (parameters.seed() != null) {
            generatorParameters.withSeed(parameters.seed());
        }
        if (!isNullOrEmpty(parameters.stopSequences())) {
            generatorParameters.withStopWords(parameters.stopSequences());
        }
        if (parameters.includeStopStrInOutput() != null) {
            generatorParameters.withIncludeStopStrInOutput(parameters.includeStopStrInOutput());
        }
        if (!isNullOrEmpty(parameters.guidedChoice())) {
            generatorParameters.withGuidedChoice(parameters.guidedChoice());
        }
        if (parameters.maxOutputTokens() != null) {
            generatorParameters.withMaxTokens(parameters.maxOutputTokens());
        }
        if (parameters.logProbs() != null) {
            generatorParameters.withLogProbs(parameters.logProbs());
        }
        if (parameters.topLogProbs() != null) {
            generatorParameters.withTopLogProbs(parameters.topLogProbs());
        }
        if (parameters.xtcThreshold() != null) {
            generatorParameters.withXtcThreshold(parameters.xtcThreshold().floatValue());
        }
        if (parameters.xtcProbability() != null) {
            generatorParameters.withXtcProbability(parameters.xtcProbability().floatValue());
        }
        if (parameters.topK() != null) {
            generatorParameters.withTopK(parameters.topK().floatValue());
        }
        if (parameters.topP() != null) {
            generatorParameters.withTopP(parameters.topP().floatValue());
        }

        return generatorParameters;
    }

    static String modelName(AutoModelForCausaLm.Builder modelBuilder) {
        ModelFetcher fetch = modelBuilder.getFetch();
        return fetch.getOwner() + "/" + fetch.getName();
    }

    static AiMessage toAiMessage(io.teknek.deliverance.generator.Response response) {
        if (response.toolCalls.isEmpty()) {
            return new AiMessage(response.responseText);
        }

        List<ToolExecutionRequest> toolExecutionRequests = response.toolCalls.stream()
                .map(toolCall -> ToolExecutionRequest.builder()
                        .id(toolCall.getId())
                        .name(toolCall.getName())
                        .arguments(Json.toJson(toolCall.getParameters()))
                        .build())
                .toList();

        if (response.responseText == null || response.responseText.isBlank()) {
            return new AiMessage(toolExecutionRequests);
        }

        return AiMessage.builder()
                .text(response.responseText)
                .toolExecutionRequests(toolExecutionRequests)
                .build();
    }

    static ChatResponse toChatResponse(io.teknek.deliverance.generator.Response response, String modelName) {
        return ChatResponse.builder()
                .aiMessage(toAiMessage(response))
                .metadata(ChatResponseMetadata.builder()
                        .modelName(modelName)
                        .tokenUsage(toTokenUsage(response))
                        .finishReason(toFinishReason(response.finishReason))
                        .build())
                .build();
    }

    static TokenUsage toTokenUsage(io.teknek.deliverance.generator.Response response) {
        return new TokenUsage(response.promptTokens, response.generatedTokens.size());
    }

    static FinishReason toFinishReason(io.teknek.deliverance.generator.FinishReason reason) {
        return switch (reason) {
            case STOP_TOKEN -> FinishReason.STOP;
            case MAX_TOKENS -> FinishReason.LENGTH;
            case TOOL_CALLS, FUNCTION_CALL -> FinishReason.TOOL_EXECUTION;
            case CONTENT_FILTER -> FinishReason.CONTENT_FILTER;
        };
    }

    private static class ConfiguredModelFetcher extends ModelFetcher {
        //private final ModelFetcher delegate;
        ConfiguredModelFetcher(Path modelCachePath, String modelName, String authToken) {
            super(owner(modelName), name(modelName), authToken);
            //delegate = new ModelFetcher(owner(modelName), name(modelName), token);
            if (modelCachePath != null) {
                this.baseDir = modelCachePath;
                if (!Files.exists(this.baseDir)) {
                    try {
                        Files.createDirectories(this.baseDir);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        }

        private static String owner(String modelName) {
            int slashIndex = modelName.indexOf('/');
            if (slashIndex < 1 || slashIndex == modelName.length() - 1) {
                throw new IllegalArgumentException("Model name must be in the form owner/name");
            }
            return modelName.substring(0, slashIndex);
        }

        private static String name(String modelName) {
            int slashIndex = modelName.indexOf('/');
            if (slashIndex < 1 || slashIndex == modelName.length() - 1) {
                throw new IllegalArgumentException("Model name must be in the form owner/name");
            }
            return modelName.substring(slashIndex + 1);
        }
    }
}
