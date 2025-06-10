package dev.langchain4j.model.openai.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.openai.OpenAiTokenUsage.InputTokensDetails;
import dev.langchain4j.model.openai.OpenAiTokenUsage.OutputTokensDetails;
import dev.langchain4j.model.openai.internal.chat.AssistantMessage;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.ContentType;
import dev.langchain4j.model.openai.internal.chat.Function;
import dev.langchain4j.model.openai.internal.chat.FunctionCall;
import dev.langchain4j.model.openai.internal.chat.FunctionMessage;
import dev.langchain4j.model.openai.internal.chat.ImageDetail;
import dev.langchain4j.model.openai.internal.chat.ImageUrl;
import dev.langchain4j.model.openai.internal.chat.InputAudio;
import dev.langchain4j.model.openai.internal.chat.Message;
import dev.langchain4j.model.openai.internal.chat.PdfFile;
import dev.langchain4j.model.openai.internal.chat.Tool;
import dev.langchain4j.model.openai.internal.chat.ToolCall;
import dev.langchain4j.model.openai.internal.chat.ToolChoiceMode;
import dev.langchain4j.model.openai.internal.chat.ToolMessage;
import dev.langchain4j.model.openai.internal.shared.CompletionTokensDetails;
import dev.langchain4j.model.openai.internal.shared.PromptTokensDetails;
import dev.langchain4j.model.openai.internal.shared.Usage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.chat.request.ResponseFormat.JSON;
import static dev.langchain4j.model.chat.request.ResponseFormatType.TEXT;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.model.openai.internal.chat.ResponseFormatType.JSON_OBJECT;
import static dev.langchain4j.model.openai.internal.chat.ResponseFormatType.JSON_SCHEMA;
import static dev.langchain4j.model.openai.internal.chat.ToolType.FUNCTION;
import static dev.langchain4j.model.output.FinishReason.CONTENT_FILTER;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

@Internal
public class OpenAiUtils {

    public static final String DEFAULT_OPENAI_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_USER_AGENT = "langchain4j-openai";

    public static List<Message> toOpenAiMessages(List<ChatMessage> messages) {
        return messages.stream().map(OpenAiUtils::toOpenAiMessage).collect(toList());
    }

    public static Message toOpenAiMessage(ChatMessage message) {
        if (message instanceof SystemMessage) {
            return dev.langchain4j.model.openai.internal.chat.SystemMessage.from(((SystemMessage) message).text());
        }

        if (message instanceof UserMessage userMessage) {

            if (userMessage.hasSingleText()) {
                return dev.langchain4j.model.openai.internal.chat.UserMessage.builder()
                        .content(userMessage.singleText())
                        .name(userMessage.name())
                        .build();
            } else {
                return dev.langchain4j.model.openai.internal.chat.UserMessage.builder()
                        .content(userMessage.contents().stream()
                                .map(OpenAiUtils::toOpenAiContent)
                                .collect(toList()))
                        .name(userMessage.name())
                        .build();
            }
        }

        if (message instanceof AiMessage aiMessage) {

            if (!aiMessage.hasToolExecutionRequests()) {
                return AssistantMessage.from(aiMessage.text());
            }

            ToolExecutionRequest toolExecutionRequest =
                    aiMessage.toolExecutionRequests().get(0);
            if (toolExecutionRequest.id() == null) {
                FunctionCall functionCall = FunctionCall.builder()
                        .name(toolExecutionRequest.name())
                        .arguments(toolExecutionRequest.arguments())
                        .build();

                return AssistantMessage.builder().functionCall(functionCall).build();
            }

            List<ToolCall> toolCalls = aiMessage.toolExecutionRequests().stream()
                    .map(it -> ToolCall.builder()
                            .id(it.id())
                            .type(FUNCTION)
                            .function(FunctionCall.builder()
                                    .name(it.name())
                                    .arguments(isNullOrBlank(it.arguments()) ? "{}" : it.arguments())
                                    .build())
                            .build())
                    .collect(toList());

            return AssistantMessage.builder()
                    .content(aiMessage.text())
                    .toolCalls(toolCalls)
                    .build();
        }

        if (message instanceof ToolExecutionResultMessage toolExecutionResultMessage) {

            if (toolExecutionResultMessage.id() == null) {
                return FunctionMessage.from(toolExecutionResultMessage.toolName(), toolExecutionResultMessage.text());
            }

            return ToolMessage.from(toolExecutionResultMessage.id(), toolExecutionResultMessage.text());
        }

        throw illegalArgument("Unknown message type: " + message.type());
    }

    private static dev.langchain4j.model.openai.internal.chat.Content toOpenAiContent(Content content) {
        if (content instanceof TextContent) {
            return toOpenAiContent((TextContent) content);
        } else if (content instanceof ImageContent) {
            return toOpenAiContent((ImageContent) content);
        } else if (content instanceof AudioContent audioContent) {
            return toOpenAiContent(audioContent);
        } else if (content instanceof PdfFileContent pdfFileContent) {
            return toOpenAiContent(pdfFileContent);
        } else {
            throw illegalArgument("Unknown content type: " + content);
        }
    }


    private static dev.langchain4j.model.openai.internal.chat.Content toOpenAiContent(TextContent content) {
        return dev.langchain4j.model.openai.internal.chat.Content.builder()
                .type(ContentType.TEXT)
                .text(content.text())
                .build();
    }

    private static dev.langchain4j.model.openai.internal.chat.Content toOpenAiContent(ImageContent content) {
        return dev.langchain4j.model.openai.internal.chat.Content.builder()
                .type(ContentType.IMAGE_URL)
                .imageUrl(ImageUrl.builder()
                        .url(toUrl(content.image()))
                        .detail(toDetail(content.detailLevel()))
                        .build())
                .build();
    }

    private static dev.langchain4j.model.openai.internal.chat.Content toOpenAiContent(AudioContent audioContent) {
        return dev.langchain4j.model.openai.internal.chat.Content.builder()
                .type(ContentType.AUDIO)
                .inputAudio(InputAudio.builder()
                        .data(ensureNotBlank(audioContent.audio().base64Data(), "audio.base64Data"))
                        .format(extractSubtype(ensureNotBlank(audioContent.audio().mimeType(), "audio.mimeType")))
                        .build())
                .build();
    }

    private static dev.langchain4j.model.openai.internal.chat.Content toOpenAiContent(PdfFileContent pdfFileContent) {
        String fileData;
        if (pdfFileContent.pdfFile().url() != null) {
            fileData = pdfFileContent.pdfFile().url().toString();
        } else {
            fileData = format("data:%s;base64,%s",
                    pdfFileContent.pdfFile().mimeType(),
                    pdfFileContent.pdfFile().base64Data());
        }

        return dev.langchain4j.model.openai.internal.chat.Content.builder()
                .type(ContentType.FILE)
                .file(PdfFile.builder()
                        .fileData(fileData)
                        .filename("pdf_file")
                        .build())
                .build();
    }


    private static String extractSubtype(String mimetype) {
        return mimetype.split("/")[1];
    }

    private static String toUrl(Image image) {
        if (image.url() != null) {
            return image.url().toString();
        }
        return format("data:%s;base64,%s", image.mimeType(), image.base64Data());
    }

    private static ImageDetail toDetail(ImageContent.DetailLevel detailLevel) {
        if (detailLevel == null) {
            return null;
        }
        return ImageDetail.valueOf(detailLevel.name());
    }

    public static List<Tool> toTools(Collection<ToolSpecification> toolSpecifications, boolean strict) {
        return toolSpecifications.stream()
                .map((ToolSpecification toolSpecification) -> toTool(toolSpecification, strict))
                .collect(toList());
    }

    private static Tool toTool(ToolSpecification toolSpecification, boolean strict) {
        Function.Builder functionBuilder = Function.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(toOpenAiParameters(toolSpecification.parameters(), strict));
        if (strict) {
            functionBuilder.strict(true);
        }
        Function function = functionBuilder.build();
        return Tool.from(function);
    }

    /**
     * @deprecated Functions are deprecated by OpenAI, use {@link #toTools(Collection, boolean)} instead
     */
    @Deprecated
    public static List<Function> toFunctions(Collection<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream().map(OpenAiUtils::toFunction).collect(toList());
    }

    /**
     * @deprecated Functions are deprecated by OpenAI, use {@link #toTool(ToolSpecification, boolean)} instead
     */
    @Deprecated
    private static Function toFunction(ToolSpecification toolSpecification) {
        return Function.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(toOpenAiParameters(toolSpecification.parameters(), false))
                .build();
    }

    private static Map<String, Object> toOpenAiParameters(JsonObjectSchema parameters, boolean strict) {
        if (parameters != null) {
            return toMap(parameters, strict);
        } else {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "object");
            map.put("properties", new HashMap<>());
            map.put("required", new ArrayList<>());
            if (strict) {
                // When strict, additionalProperties must be false:
                // See https://platform.openai.com/docs/guides/structured-outputs/additionalproperties-false-must-always-be-set-in-objects?api-mode=chat#additionalproperties-false-must-always-be-set-in-objects
                map.put("additionalProperties", false);
            }
            return map;
        }
    }

    public static AiMessage aiMessageFrom(ChatCompletionResponse response) {
        AssistantMessage assistantMessage = response.choices().get(0).message();
        String text = assistantMessage.content();

        List<ToolCall> toolCalls = assistantMessage.toolCalls();
        if (!isNullOrEmpty(toolCalls)) {
            List<ToolExecutionRequest> toolExecutionRequests = toolCalls.stream()
                    .filter(toolCall -> toolCall.type() == FUNCTION)
                    .map(OpenAiUtils::toToolExecutionRequest)
                    .collect(toList());
            return isNullOrBlank(text)
                    ? AiMessage.from(toolExecutionRequests)
                    : AiMessage.from(text, toolExecutionRequests);
        }

        FunctionCall functionCall = assistantMessage.functionCall();
        if (functionCall != null) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(functionCall.name())
                    .arguments(functionCall.arguments())
                    .build();
            return isNullOrBlank(text)
                    ? AiMessage.from(toolExecutionRequest)
                    : AiMessage.from(text, singletonList(toolExecutionRequest));
        }

        return AiMessage.from(text);
    }

    private static ToolExecutionRequest toToolExecutionRequest(ToolCall toolCall) {
        FunctionCall functionCall = toolCall.function();
        return ToolExecutionRequest.builder()
                .id(toolCall.id())
                .name(functionCall.name())
                .arguments(functionCall.arguments())
                .build();
    }

    public static OpenAiTokenUsage tokenUsageFrom(Usage openAiUsage) {
        if (openAiUsage == null) {
            return null;
        }

        PromptTokensDetails promptTokensDetails = openAiUsage.promptTokensDetails();
        InputTokensDetails inputTokensDetails = null;
        if (promptTokensDetails != null) {
            inputTokensDetails = InputTokensDetails.builder()
                    .cachedTokens(promptTokensDetails.cachedTokens())
                    .build();
        }

        CompletionTokensDetails completionTokensDetails = openAiUsage.completionTokensDetails();
        OutputTokensDetails outputTokensDetails = null;
        if (completionTokensDetails != null) {
            outputTokensDetails = OutputTokensDetails.builder()
                    .reasoningTokens(completionTokensDetails.reasoningTokens())
                    .build();
        }

        return OpenAiTokenUsage.builder()
                .inputTokenCount(openAiUsage.promptTokens())
                .inputTokensDetails(inputTokensDetails)
                .outputTokenCount(openAiUsage.completionTokens())
                .outputTokensDetails(outputTokensDetails)
                .totalTokenCount(openAiUsage.totalTokens())
                .build();
    }

    public static FinishReason finishReasonFrom(String openAiFinishReason) {
        if (openAiFinishReason == null) {
            return null;
        }
        switch (openAiFinishReason) {
            case "stop":
                return STOP;
            case "length":
                return LENGTH;
            case "tool_calls":
            case "function_call":
                return TOOL_EXECUTION;
            case "content_filter":
                return CONTENT_FILTER;
            default:
                return null;
        }
    }

    static dev.langchain4j.model.openai.internal.chat.ResponseFormat toOpenAiResponseFormat(ResponseFormat responseFormat, Boolean strict) {
        if (responseFormat == null || responseFormat.type() == TEXT) {
            return null;
        }

        JsonSchema jsonSchema = responseFormat.jsonSchema();
        if (jsonSchema == null) {
            return dev.langchain4j.model.openai.internal.chat.ResponseFormat.builder()
                    .type(JSON_OBJECT)
                    .build();
        } else {
            if (!(jsonSchema.rootElement() instanceof JsonObjectSchema)) {
                throw new IllegalArgumentException(
                        "For OpenAI, the root element of the JSON Schema must be a JsonObjectSchema, but it was: "
                                + jsonSchema.rootElement().getClass());
            }
            dev.langchain4j.model.openai.internal.chat.JsonSchema openAiJsonSchema = dev.langchain4j.model.openai.internal.chat.JsonSchema.builder()
                    .name(jsonSchema.name())
                    .strict(strict)
                    .schema(toMap(jsonSchema.rootElement(), strict))
                    .build();
            return dev.langchain4j.model.openai.internal.chat.ResponseFormat.builder()
                    .type(JSON_SCHEMA)
                    .jsonSchema(openAiJsonSchema)
                    .build();
        }
    }

    public static ToolChoiceMode toOpenAiToolChoice(ToolChoice toolChoice) {
        if (toolChoice == null) {
            return null;
        }

        return switch (toolChoice) {
            case AUTO -> ToolChoiceMode.AUTO;
            case REQUIRED -> ToolChoiceMode.REQUIRED;
        };
    }

    public static Response<AiMessage> convertResponse(ChatResponse chatResponse) {
        return Response.from(
                chatResponse.aiMessage(),
                chatResponse.metadata().tokenUsage(),
                chatResponse.metadata().finishReason());
    }

    public static void validate(ChatRequestParameters parameters) {
        if (parameters.topK() != null) {
            throw new UnsupportedFeatureException("'topK' parameter is not supported by OpenAI");
        }
    }

    public static ResponseFormat fromOpenAiResponseFormat(String responseFormat) {
        if ("json_object".equals(responseFormat)) {
            return JSON;
        } else {
            return null;
        }
    }

    public static ChatCompletionRequest.Builder toOpenAiChatRequest(
            ChatRequest chatRequest,
            OpenAiChatRequestParameters parameters,
            Boolean strictTools,
            Boolean strictJsonSchema) {
        return ChatCompletionRequest.builder()
                .messages(toOpenAiMessages(chatRequest.messages()))
                // common parameters
                .model(parameters.modelName())
                .temperature(parameters.temperature())
                .topP(parameters.topP())
                .frequencyPenalty(parameters.frequencyPenalty())
                .presencePenalty(parameters.presencePenalty())
                .maxTokens(parameters.maxOutputTokens())
                .stop(parameters.stopSequences())
                .tools(toTools(parameters.toolSpecifications(), strictTools))
                .toolChoice(toOpenAiToolChoice(parameters.toolChoice()))
                .responseFormat(toOpenAiResponseFormat(parameters.responseFormat(), strictJsonSchema))
                // OpenAI-specific parameters
                .maxCompletionTokens(parameters.maxCompletionTokens())
                .logitBias(parameters.logitBias())
                .parallelToolCalls(parameters.parallelToolCalls())
                .seed(parameters.seed())
                .user(parameters.user())
                .store(parameters.store())
                .metadata(parameters.metadata())
                .serviceTier(parameters.serviceTier())
                .reasoningEffort(parameters.reasoningEffort());
    }
}
