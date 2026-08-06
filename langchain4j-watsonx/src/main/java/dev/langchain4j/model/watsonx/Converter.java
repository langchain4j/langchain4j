package dev.langchain4j.model.watsonx;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import com.ibm.watsonx.ai.chat.ChatResponse.ResultChoice;
import com.ibm.watsonx.ai.chat.TextChatResponse;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.BaseChatParameters;
import com.ibm.watsonx.ai.chat.model.BaseChatParameters.ToolChoiceOption;
import com.ibm.watsonx.ai.chat.model.ChatParameters;
import com.ibm.watsonx.ai.chat.model.ChatUsage;
import com.ibm.watsonx.ai.chat.model.Image.Detail;
import com.ibm.watsonx.ai.chat.model.ImageContent;
import com.ibm.watsonx.ai.chat.model.TextContent;
import com.ibm.watsonx.ai.chat.model.Tool;
import com.ibm.watsonx.ai.chat.model.ToolCall;
import com.ibm.watsonx.ai.chat.model.ToolMessage;
import com.ibm.watsonx.ai.chat.model.UserContent;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayChatResponse;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;

@Internal
class Converter {

    static com.ibm.watsonx.ai.chat.model.ChatMessage toChatMessage(ChatMessage chatMessage) {
        return switch (chatMessage.type()) {
            case SYSTEM -> toSystemMessage(SystemMessage.class.cast(chatMessage));
            case AI -> toAssistantMessage(AiMessage.class.cast(chatMessage));
            case USER -> toUserMessage(UserMessage.class.cast(chatMessage));
            case CUSTOM -> throw new UnsupportedOperationException("The custom message type is not supported");
            case TOOL_EXECUTION_RESULT -> toToolMessage(ToolExecutionResultMessage.class.cast(chatMessage));
        };
    }

    static Tool toTool(ToolSpecification toolSpecification) {
        var parameters = nonNull(toolSpecification.parameters())
                ? JsonSchemaElementUtils.toMap(toolSpecification.parameters())
                : null;
        return Tool.of(toolSpecification.name(), toolSpecification.description(), parameters);
    }

    static ToolExecutionRequest toToolExecutionRequest(ToolCall toolCall) {
        return ToolExecutionRequest.builder()
                .arguments(toolCall.function().arguments())
                .id(toolCall.id())
                .name(toolCall.function().name())
                .build();
    }

    static FinishReason toFinishReason(String finishReason) {
        if (finishReason == null) return FinishReason.OTHER;

        return switch (finishReason) {
            case "length" -> FinishReason.LENGTH;
            case "stop" -> FinishReason.STOP;
            case "tool_calls" -> FinishReason.TOOL_EXECUTION;
            case "time_limit", "cancelled", "error" -> FinishReason.OTHER;
            default -> throw new IllegalArgumentException("%s not supported".formatted(finishReason));
        };
    }

    static CompleteToolCall toCompleteToolCall(ToolCall toolCall) {
        return new CompleteToolCall(toolCall.index(), toToolExecutionRequest(toolCall));
    }

    static PartialToolCall toPartialToolCall(com.ibm.watsonx.ai.chat.model.PartialToolCall partialToolCall) {
        return PartialToolCall.builder()
                .id(partialToolCall.id())
                .index(partialToolCall.toolIndex())
                .name(partialToolCall.name())
                .partialArguments(partialToolCall.arguments())
                .build();
    }

    static ChatResponse toChatResponse(TextChatResponse textChatResponse) {

        AssistantMessage assistantMessage = textChatResponse.toAssistantMessage();
        ResultChoice choice = textChatResponse.choices().get(0);
        ChatUsage usage = textChatResponse.usage();

        AiMessage.Builder aiMessage = AiMessage.builder();

        if (nonNull(assistantMessage.toolCalls())
                && !assistantMessage.toolCalls().isEmpty()) {
            var toolExecutionRequests = assistantMessage.toolCalls().stream()
                    .map(Converter::toToolExecutionRequest)
                    .toList();
            aiMessage.toolExecutionRequests(toolExecutionRequests);
        }

        aiMessage.thinking(assistantMessage.thinking());
        aiMessage.text(assistantMessage.content());

        TokenUsage tokenUsage = nonNull(usage)
                ? new TokenUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens())
                : null;

        var metadata = WatsonxChatResponseMetadata.builder()
                .created(textChatResponse.created())
                .modelVersion(textChatResponse.modelVersion())
                .finishReason(toFinishReason(choice.finishReason()))
                .id(textChatResponse.id())
                .modelName(textChatResponse.modelId())
                .tokenUsage(tokenUsage);

        if (textChatResponse instanceof ModelGatewayChatResponse gatewayResponse) {

            metadata.modelName(gatewayResponse.model())
                    .serviceTier(gatewayResponse.serviceTier())
                    .systemFingerprint(gatewayResponse.systemFingerprint())
                    .cached(gatewayResponse.cached());
        }

        return ChatResponse.builder()
                .aiMessage(aiMessage.build())
                .metadata(metadata.build())
                .build();
    }

    static ChatParameters toChatParameters(ChatRequestParameters parameters, boolean strictJsonSchema) {

        ChatParameters.Builder builder = ChatParameters.builder();
        applyBaseParameters(builder, parameters, strictJsonSchema);

        if (parameters instanceof WatsonxChatRequestParameters watsonxParameters) {
            builder.logitBias(watsonxParameters.logitBias());
            builder.logprobs(watsonxParameters.logprobs());
            builder.topLogprobs(watsonxParameters.topLogprobs());
            builder.seed(watsonxParameters.seed());
            builder.timeLimit(watsonxParameters.timeout());
            applyToolChoice(builder, parameters, watsonxParameters.toolChoiceName());
            builder.projectId(watsonxParameters.projectId());
            builder.spaceId(watsonxParameters.spaceId());
            builder.guidedChoice(watsonxParameters.guidedChoice());
            builder.guidedGrammar(watsonxParameters.guidedGrammar());
            builder.guidedRegex(watsonxParameters.guidedRegex());
            builder.repetitionPenalty(watsonxParameters.repetitionPenalty());
            builder.lengthPenalty(watsonxParameters.lengthPenalty());
        }

        return builder.build();
    }

    static ModelGatewayParameters toModelGatewayParameters(ChatRequestParameters parameters, boolean strictJsonSchema) {

        ModelGatewayParameters.Builder builder = ModelGatewayParameters.builder();
        applyBaseParameters(builder, parameters, strictJsonSchema);

        if (parameters instanceof WatsonxGatewayChatRequestParameters gatewayParameters) {
            builder.logitBias(gatewayParameters.logitBias());
            builder.logprobs(gatewayParameters.logprobs());
            builder.topLogprobs(gatewayParameters.topLogprobs());
            builder.seed(gatewayParameters.seed());
            builder.timeLimit(gatewayParameters.timeout());
            applyToolChoice(builder, parameters, gatewayParameters.toolChoiceName());
            builder.serviceTier(gatewayParameters.serviceTier());
            builder.reasoningEffort(gatewayParameters.reasoningEffort());
            builder.router(gatewayParameters.router());
            builder.modalities(gatewayParameters.modalities());
            builder.store(gatewayParameters.store());
            builder.parallelToolCalls(gatewayParameters.parallelToolCalls());
            builder.user(gatewayParameters.user());
            builder.metadata(gatewayParameters.metadata());
        }

        return builder.build();
    }

    private static void applyBaseParameters(
            BaseChatParameters.Builder<?> builder, ChatRequestParameters parameters, boolean strictJsonSchema) {

        builder.modelId(parameters.modelName());
        builder.frequencyPenalty(parameters.frequencyPenalty());
        builder.maxCompletionTokens(parameters.maxOutputTokens());
        builder.presencePenalty(parameters.presencePenalty());
        builder.temperature(parameters.temperature());
        builder.topP(parameters.topP());

        // DefaultChatRequestParameters returns an empty list when no stop sequence is set, and the Model Gateway
        // rejects an empty "stop" array, so the field is sent only when there is at least one sequence.
        List<String> stopSequences = parameters.stopSequences();

        if (nonNull(stopSequences) && !stopSequences.isEmpty()) {
            builder.stop(stopSequences);
        }

        ResponseFormat responseFormat = parameters.responseFormat();

        if (nonNull(responseFormat)) {
            switch (responseFormat.type()) {
                case JSON -> {
                    if (nonNull(responseFormat.jsonSchema())) {

                        var name = responseFormat.jsonSchema().name();
                        var rootElement = responseFormat.jsonSchema().rootElement();

                        if (!(rootElement instanceof JsonObjectSchema || rootElement instanceof JsonRawSchema))
                            throw new IllegalArgumentException(
                                    "The root element of the JSON Schema must be either a JsonObjectSchema or a JsonRawSchema, but it was: "
                                            + rootElement.getClass());

                        var jsonSchema = JsonSchemaElementUtils.toMap(rootElement, strictJsonSchema);
                        builder.responseAsJsonSchema(name, jsonSchema, strictJsonSchema);
                    } else {
                        builder.responseAsJson();
                    }
                }
                case TEXT -> {
                    // Do nothing.
                }
            }
        }
    }

    private static void applyToolChoice(
            BaseChatParameters.Builder<?> builder, ChatRequestParameters parameters, String toolChoiceName) {

        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        ToolChoice toolChoice = parameters.toolChoice();

        if ((isNull(toolChoice) || toolChoice.equals(ToolChoice.REQUIRED)) && nonNull(toolChoiceName)) {

            if (toolSpecifications.isEmpty())
                throw new IllegalArgumentException("If tool-choice-name is set, at least one tool must be specified.");

            builder.toolChoiceOption(null);
            builder.toolChoice(toolSpecifications.stream()
                    .filter(toolSpecification -> toolSpecification.name().equalsIgnoreCase(toolChoiceName))
                    .findFirst()
                    .map(ToolSpecification::name)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "The tool with name '%s' is not available in the list of tools sent to the model."
                                    .formatted(toolChoiceName))));

        } else if (nonNull(toolChoice)) {
            switch (toolChoice) {
                case AUTO -> builder.toolChoiceOption(ToolChoiceOption.AUTO);
                case REQUIRED -> {
                    if (toolSpecifications.isEmpty())
                        throw new IllegalArgumentException(
                                "If tool-choice is 'REQUIRED', at least one tool must be specified.");

                    builder.toolChoiceOption(ToolChoiceOption.REQUIRED);
                }
                case NONE -> builder.toolChoiceOption(ToolChoiceOption.NONE);
            }
        }
    }

    private static ToolCall toToolCall(ToolExecutionRequest toolExecutionRequest) {
        return ToolCall.of(toolExecutionRequest.id(), toolExecutionRequest.name(), toolExecutionRequest.arguments());
    }

    private static com.ibm.watsonx.ai.chat.model.SystemMessage toSystemMessage(SystemMessage systemMessage) {
        return com.ibm.watsonx.ai.chat.model.SystemMessage.of(systemMessage.text());
    }

    private static AssistantMessage toAssistantMessage(AiMessage aiMessage) {
        List<ToolCall> toolCalls = null;
        if (aiMessage.hasToolExecutionRequests()) {
            toolCalls = aiMessage.toolExecutionRequests().stream()
                    .map(Converter::toToolCall)
                    .toList();
        }
        return new AssistantMessage(AssistantMessage.ROLE, aiMessage.text(), null, null, null, toolCalls);
    }

    private static com.ibm.watsonx.ai.chat.model.UserMessage toUserMessage(UserMessage userMessage) {
        return com.ibm.watsonx.ai.chat.model.UserMessage.of(
                userMessage.name(),
                userMessage.contents().stream().map(Converter::toUserContent).toList());
    }

    private static UserContent toUserContent(Content content) {
        return switch (content.type()) {
            case AUDIO, VIDEO, PDF -> throw new RuntimeException("Not implemented");
            case IMAGE -> {
                var imageContent = (dev.langchain4j.data.message.ImageContent) content;

                if (nonNull(imageContent.image().url()))
                    throw new UnsupportedFeatureException("image URL is not supported");

                var mimeType = imageContent.image().mimeType();
                var base64Data = requireNonNull(imageContent.image().base64Data(), "The base64Data can not be null");
                Detail detailLevel =
                        switch (imageContent.detailLevel()) {
                            case AUTO -> Detail.AUTO;
                            case HIGH -> Detail.HIGH;
                            case LOW -> Detail.LOW;
                            default ->
                                throw new UnsupportedFeatureException(
                                        "Unsupported detail level: " + imageContent.detailLevel());
                        };
                yield ImageContent.of(mimeType, base64Data, detailLevel);
            }
            case TEXT -> {
                var textContent = (dev.langchain4j.data.message.TextContent) content;
                yield TextContent.of(textContent.text());
            }
        };
    }

    private static ToolMessage toToolMessage(ToolExecutionResultMessage toolExecutionResultMessage) {
        if (!toolExecutionResultMessage.hasSingleText()) {
            throw new UnsupportedFeatureException(
                    "watsonx does not support non-text content in tool results. Only text content is supported.");
        }
        return ToolMessage.of(toolExecutionResultMessage.text(), toolExecutionResultMessage.id());
    }
}
