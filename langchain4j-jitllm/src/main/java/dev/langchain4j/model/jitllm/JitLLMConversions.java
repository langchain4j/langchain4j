package dev.langchain4j.model.jitllm;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.beehive.jitllm.api.CancellationToken;
import org.beehive.jitllm.api.ChatContent;
import org.beehive.jitllm.api.ChatRole;
import org.beehive.jitllm.api.GenerationEvent;
import org.beehive.jitllm.api.GenerationRequest;
import org.beehive.jitllm.api.GenerationResult;
import org.beehive.jitllm.api.ToolSpec;

final class JitLLMConversions {

    private static final String NOT_SUPPORTED = "%s is not supported yet by this model provider";
    private static final String NO_PARAMETERS_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private JitLLMConversions() {}

    static ChatRequestParameters defaultRequestParameters(
            ChatRequestParameters parameters,
            Double temperature,
            Double topP,
            Integer maxTokens,
            List<String> stopSequences) {
        ChatRequestParameters commonParameters = getOrDefault(parameters, DefaultChatRequestParameters.EMPTY);
        validate(commonParameters);
        return DefaultChatRequestParameters.builder()
                .overrideWith(commonParameters)
                .temperature(getOrDefault(temperature, commonParameters.temperature()))
                .topP(getOrDefault(topP, commonParameters.topP()))
                .maxOutputTokens(getOrDefault(maxTokens, commonParameters.maxOutputTokens()))
                .stopSequences(getOrDefault(stopSequences, commonParameters.stopSequences()))
                .build();
    }

    static void validate(ChatRequest chatRequest) {
        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
        validate(chatRequest.parameters());
    }

    static void validate(ChatRequestParameters parameters) {
        if (parameters.modelName() != null) {
            throw new UnsupportedFeatureException(String.format(NOT_SUPPORTED, "'modelName' parameter"));
        }
        if (parameters.topK() != null) {
            throw new UnsupportedFeatureException(String.format(NOT_SUPPORTED, "'topK' parameter"));
        }
        if (parameters.frequencyPenalty() != null) {
            throw new UnsupportedFeatureException(String.format(NOT_SUPPORTED, "'frequencyPenalty' parameter"));
        }
        if (parameters.presencePenalty() != null) {
            throw new UnsupportedFeatureException(String.format(NOT_SUPPORTED, "'presencePenalty' parameter"));
        }
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());
    }

    static GenerationRequest toGenerationRequest(
            ChatRequest chatRequest,
            Integer seed,
            Consumer<GenerationEvent> eventConsumer,
            CancellationToken cancellationToken) {
        ChatRequestParameters parameters = chatRequest.parameters();
        GenerationRequest.Builder builder =
                GenerationRequest.builder().messages(toEngineMessages(chatRequest.messages()));
        if (parameters.temperature() != null) {
            builder.temperature(parameters.temperature().floatValue());
        }
        if (parameters.topP() != null) {
            builder.topP(parameters.topP().floatValue());
        }
        if (parameters.maxOutputTokens() != null) {
            builder.maxNewTokens(parameters.maxOutputTokens());
        }
        if (!parameters.stopSequences().isEmpty()) {
            builder.stopSequences(parameters.stopSequences());
        }
        if (!chatRequest.toolSpecifications().isEmpty()) {
            builder.tools(toEngineTools(chatRequest.toolSpecifications()));
        }
        if (seed != null) {
            builder.seed(seed);
        }
        if (eventConsumer != null) {
            builder.onEvent(eventConsumer);
        }
        if (cancellationToken != null) {
            builder.cancellation(cancellationToken);
        }
        return builder.build();
    }

    static List<org.beehive.jitllm.api.ChatMessage> toEngineMessages(List<ChatMessage> messages) {
        List<org.beehive.jitllm.api.ChatMessage> converted = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage userMessage) {
                converted.add(org.beehive.jitllm.api.ChatMessage.of(ChatRole.USER, userMessage.singleText()));
            } else if (message instanceof SystemMessage systemMessage) {
                converted.add(org.beehive.jitllm.api.ChatMessage.of(ChatRole.SYSTEM, systemMessage.text()));
            } else if (message instanceof AiMessage aiMessage) {
                converted.add(toAssistantMessage(aiMessage));
            } else if (message instanceof ToolExecutionResultMessage toolResult) {
                converted.add(new org.beehive.jitllm.api.ChatMessage(
                        ChatRole.TOOL,
                        List.of(new ChatContent.ToolResult(
                                blankToGenerated(toolResult.id()),
                                toolResult.toolName(),
                                unwrapToolResult(toolResult.text())))));
            } else {
                throw new UnsupportedFeatureException(
                        String.format(NOT_SUPPORTED, message.type() + " message type"));
            }
        }
        return converted;
    }

    static org.beehive.jitllm.api.ChatMessage toAssistantMessage(AiMessage aiMessage) {
        List<ChatContent> content = new ArrayList<>();
        if (aiMessage.text() != null && !aiMessage.text().isEmpty()) {
            content.add(new ChatContent.Text(aiMessage.text()));
        }
        if (aiMessage.hasToolExecutionRequests()) {
            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                String arguments = toolExecutionRequest.arguments();
                content.add(new ChatContent.ToolCall(
                        blankToGenerated(toolExecutionRequest.id()),
                        toolExecutionRequest.name(),
                        arguments == null || arguments.isBlank() ? "{}" : arguments));
            }
        }
        if (content.isEmpty()) {
            content.add(new ChatContent.Text("")); // the engine does not accept an assistant turn without content
        }
        return new org.beehive.jitllm.api.ChatMessage(ChatRole.ASSISTANT, content);
    }

    static List<ToolSpec> toEngineTools(List<ToolSpecification> toolSpecifications) {
        List<ToolSpec> toolSpecs = new ArrayList<>(toolSpecifications.size());
        for (ToolSpecification toolSpecification : toolSpecifications) {
            String parameters = toolSpecification.parameters() == null
                    ? NO_PARAMETERS_SCHEMA
                    : Json.toJson(JsonSchemaElementUtils.toMap(toolSpecification.parameters()));
            toolSpecs.add(new ToolSpec(
                    toolSpecification.name(), getOrDefault(toolSpecification.description(), ""), parameters));
        }
        return toolSpecs;
    }

    static List<ToolExecutionRequest> toToolExecutionRequests(List<ChatContent.ToolCall> toolCalls) {
        List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>(toolCalls.size());
        for (ChatContent.ToolCall toolCall : toolCalls) {
            toolExecutionRequests.add(ToolExecutionRequest.builder()
                    .id(toolCall.id())
                    .name(toolCall.name())
                    .arguments(normalizeJson(toolCall.argumentsJson()))
                    .build());
        }
        return toolExecutionRequests;
    }

    static FinishReason toFinishReason(org.beehive.jitllm.api.FinishReason finishReason) {
        return switch (finishReason) {
            case TOOL_CALL -> FinishReason.TOOL_EXECUTION;
            case MAX_TOKENS, CONTEXT_FULL -> FinishReason.LENGTH;
            case STOP_TOKEN, STOP_SEQUENCE -> FinishReason.STOP;
            case CANCELLED -> FinishReason.OTHER;
        };
    }

    static TokenUsage toTokenUsage(GenerationResult result) {
        return new TokenUsage(result.promptTokens(), result.generatedTokens());
    }

    static String unwrapToolResult(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() > 1 && text.startsWith("\"") && text.endsWith("\"")) {
            try {
                return Json.fromJson(text, String.class);
            } catch (RuntimeException notAJsonString) {
                return text;
            }
        }
        return text;
    }

    static String normalizeJson(String json) {
        try {
            return Json.toJson(Json.fromJson(json, Object.class));
        } catch (RuntimeException notJson) {
            return json;
        }
    }

    private static String blankToGenerated(String id) {
        return id == null || id.isBlank() ? generateCallId() : id; // the engine matches tool results to calls by id
    }

    private static String generateCallId() {
        return "call_" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
    }
}
