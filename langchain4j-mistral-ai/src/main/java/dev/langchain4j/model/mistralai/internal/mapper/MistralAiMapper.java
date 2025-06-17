package dev.langchain4j.model.mistralai.internal.mapper;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.output.FinishReason.CONTENT_FILTER;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatMessage;
import dev.langchain4j.model.mistralai.internal.api.MistralAiFunction;
import dev.langchain4j.model.mistralai.internal.api.MistralAiFunctionCall;
import dev.langchain4j.model.mistralai.internal.api.MistralAiImageBase64Content;
import dev.langchain4j.model.mistralai.internal.api.MistralAiImageUrlContent;
import dev.langchain4j.model.mistralai.internal.api.MistralAiMessageContent;
import dev.langchain4j.model.mistralai.internal.api.MistralAiParameters;
import dev.langchain4j.model.mistralai.internal.api.MistralAiResponseFormat;
import dev.langchain4j.model.mistralai.internal.api.MistralAiResponseFormatType;
import dev.langchain4j.model.mistralai.internal.api.MistralAiRole;
import dev.langchain4j.model.mistralai.internal.api.MistralAiTextContent;
import dev.langchain4j.model.mistralai.internal.api.MistralAiTool;
import dev.langchain4j.model.mistralai.internal.api.MistralAiToolCall;
import dev.langchain4j.model.mistralai.internal.api.MistralAiToolType;
import dev.langchain4j.model.mistralai.internal.api.MistralAiUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;

public class MistralAiMapper {

    public static List<MistralAiChatMessage> toMistralAiMessages(List<ChatMessage> messages) {
        return messages.stream().map(MistralAiMapper::toMistralAiMessage).collect(toList());
    }

    static MistralAiChatMessage toMistralAiMessage(ChatMessage message) {
        if (message instanceof SystemMessage) {
            return MistralAiChatMessage.builder()
                    .role(MistralAiRole.SYSTEM)
                    .content(((SystemMessage) message).text())
                    .build();
        }

        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;

            if (!aiMessage.hasToolExecutionRequests()) {
                return MistralAiChatMessage.builder()
                        .role(MistralAiRole.ASSISTANT)
                        .content(aiMessage.text())
                        .build();
            }

            List<MistralAiToolCall> toolCalls = aiMessage.toolExecutionRequests().stream()
                    .map(MistralAiMapper::toMistralAiToolCall)
                    .collect(toList());

            if (isNullOrBlank(aiMessage.text())) {
                return MistralAiChatMessage.builder()
                        .role(MistralAiRole.ASSISTANT)
                        .toolCalls(toolCalls)
                        .build();
            }

            return MistralAiChatMessage.builder()
                    .role(MistralAiRole.ASSISTANT)
                    .content(aiMessage.text())
                    .toolCalls(toolCalls)
                    .build();
        }

        if (message instanceof UserMessage userMessage) {
            return MistralAiChatMessage.builder()
                    .role(MistralAiRole.USER)
                    .content(toMistralAiMessageContents(userMessage))
                    .build();
        }

        if (message instanceof ToolExecutionResultMessage) {
            return MistralAiChatMessage.builder()
                    .role(MistralAiRole.TOOL)
                    .toolCallId(((ToolExecutionResultMessage) message).id())
                    .name(((ToolExecutionResultMessage) message).toolName())
                    .content(((ToolExecutionResultMessage) message).text())
                    .build();
        }

        throw new IllegalArgumentException("Unknown message type: " + message.type());
    }

    static MistralAiToolCall toMistralAiToolCall(ToolExecutionRequest toolExecutionRequest) {
        return MistralAiToolCall.builder()
                .id(toolExecutionRequest.id())
                .function(MistralAiFunctionCall.builder()
                        .name(toolExecutionRequest.name())
                        .arguments(toolExecutionRequest.arguments())
                        .build())
                .build();
    }

    public static TokenUsage tokenUsageFrom(MistralAiUsage mistralAiUsage) {
        if (mistralAiUsage == null) {
            return null;
        }
        return new TokenUsage(
                mistralAiUsage.getPromptTokens(),
                mistralAiUsage.getCompletionTokens(),
                mistralAiUsage.getTotalTokens());
    }

    public static FinishReason finishReasonFrom(String mistralAiFinishReason) {
        if (mistralAiFinishReason == null) {
            return null;
        }
        switch (mistralAiFinishReason) {
            case "stop":
                return STOP;
            case "length":
                return LENGTH;
            case "tool_calls":
                return TOOL_EXECUTION;
            case "content_filter":
                return CONTENT_FILTER;
            case "model_length":
            default:
                return null;
        }
    }

    public static AiMessage aiMessageFrom(MistralAiChatCompletionResponse response) {
        MistralAiChatMessage aiMistralMessage = response.getChoices().get(0).getMessage();
        List<MistralAiToolCall> toolCalls = aiMistralMessage.getToolCalls();
        if (!isNullOrEmpty(toolCalls)) {
            return AiMessage.from(toToolExecutionRequests(toolCalls));
        }
        return AiMessage.from(aiMistralMessage.asText());
    }

    public static List<ToolExecutionRequest> toToolExecutionRequests(List<MistralAiToolCall> mistralAiToolCalls) {
        return mistralAiToolCalls.stream()
                .filter(toolCall -> toolCall.getType() == MistralAiToolType.FUNCTION)
                .map(MistralAiMapper::toToolExecutionRequest)
                .collect(toList());
    }

    public static ToolExecutionRequest toToolExecutionRequest(MistralAiToolCall mistralAiToolCall) {
        return ToolExecutionRequest.builder()
                .id(mistralAiToolCall.getId())
                .name(mistralAiToolCall.getFunction().getName())
                .arguments(mistralAiToolCall.getFunction().getArguments())
                .build();
    }

    public static List<MistralAiTool> toMistralAiTools(List<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream().map(MistralAiMapper::toMistralAiTool).collect(toList());
    }

    static MistralAiTool toMistralAiTool(ToolSpecification toolSpecification) {
        MistralAiFunction function = MistralAiFunction.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(toMistralAiParameters(toolSpecification))
                .build();
        return MistralAiTool.from(function);
    }

    static MistralAiParameters toMistralAiParameters(ToolSpecification toolSpecification) {
        if (toolSpecification.parameters() != null) {
            JsonObjectSchema parameters = toolSpecification.parameters();
            return MistralAiParameters.builder()
                    .properties(toMap(parameters.properties()))
                    .required(parameters.required())
                    .build();
        } else {
            return MistralAiParameters.builder().build();
        }
    }

    public static MistralAiResponseFormat toMistralAiResponseFormat(ResponseFormat responseFormat) {
        if (responseFormat == null) {
            return null;
        }
        return switch (responseFormat.type()) {
            case TEXT -> MistralAiResponseFormat.fromType(MistralAiResponseFormatType.TEXT);
            case JSON -> responseFormat.jsonSchema() != null
                    ? MistralAiResponseFormat.fromSchema(responseFormat.jsonSchema())
                    : MistralAiResponseFormat.fromType(MistralAiResponseFormatType.JSON_OBJECT);
        };
    }

    private static List<MistralAiMessageContent> toMistralAiMessageContents(UserMessage message) {
        return message.contents().stream()
                .map(content -> {
                    if (content instanceof final TextContent textContent) {
                        return new MistralAiTextContent(textContent.text());
                    } else if (content instanceof ImageContent imageContent) {
                        Image image = imageContent.image();
                        return image.url() != null ?
                                new MistralAiImageUrlContent(image.url().toString()) :
                                new MistralAiImageBase64Content(image.base64Data());
                    } else {
                        throw illegalArgument("Unknown content type: " + content);
                    }
                })
                .collect(toList());
    }
}
