package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.chat.AssistantMessage;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.chat.ContentType;
import dev.ai4j.openai4j.chat.Function;
import dev.ai4j.openai4j.chat.FunctionCall;
import dev.ai4j.openai4j.chat.FunctionMessage;
import dev.ai4j.openai4j.chat.ImageDetail;
import dev.ai4j.openai4j.chat.ImageUrl;
import dev.ai4j.openai4j.chat.Message;
import dev.ai4j.openai4j.chat.Tool;
import dev.ai4j.openai4j.chat.ToolCall;
import dev.ai4j.openai4j.chat.ToolMessage;
import dev.ai4j.openai4j.shared.Usage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.ai4j.openai4j.chat.ResponseFormatType.JSON_OBJECT;
import static dev.ai4j.openai4j.chat.ResponseFormatType.JSON_SCHEMA;
import static dev.ai4j.openai4j.chat.ToolType.FUNCTION;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.chat.request.ResponseFormatType.TEXT;
import static dev.langchain4j.model.output.FinishReason.CONTENT_FILTER;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class InternalOpenAiHelper {

    static final String OPENAI_URL = "https://api.openai.com/v1";

    static final String OPENAI_DEMO_API_KEY = "demo";
    static final String OPENAI_DEMO_URL = "http://langchain4j.dev/demo/openai/v1";


    static final String DEFAULT_USER_AGENT = "langchain4j-openai";

    public static List<Message> toOpenAiMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(InternalOpenAiHelper::toOpenAiMessage)
                .collect(toList());
    }

    public static Message toOpenAiMessage(ChatMessage message) {
        if (message instanceof SystemMessage) {
            return dev.ai4j.openai4j.chat.SystemMessage.from(((SystemMessage) message).text());
        }

        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;

            if (userMessage.hasSingleText()) {
                return dev.ai4j.openai4j.chat.UserMessage.builder()
                        .content(userMessage.text())
                        .name(userMessage.name())
                        .build();
            } else {
                return dev.ai4j.openai4j.chat.UserMessage.builder()
                        .content(userMessage.contents().stream()
                                .map(InternalOpenAiHelper::toOpenAiContent)
                                .collect(toList()))
                        .name(userMessage.name())
                        .build();
            }
        }

        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;

            if (!aiMessage.hasToolExecutionRequests()) {
                return AssistantMessage.from(aiMessage.text());
            }

            ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
            if (toolExecutionRequest.id() == null) {
                FunctionCall functionCall = FunctionCall.builder()
                        .name(toolExecutionRequest.name())
                        .arguments(toolExecutionRequest.arguments())
                        .build();

                return AssistantMessage.builder()
                        .functionCall(functionCall)
                        .build();
            }

            List<ToolCall> toolCalls = aiMessage.toolExecutionRequests().stream()
                    .map(it -> ToolCall.builder()
                            .id(it.id())
                            .type(FUNCTION)
                            .function(FunctionCall.builder()
                                    .name(it.name())
                                    .arguments(it.arguments())
                                    .build())
                            .build())
                    .collect(toList());

            return AssistantMessage.builder()
                    .toolCalls(toolCalls)
                    .build();
        }

        if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) message;

            if (toolExecutionResultMessage.id() == null) {
                return FunctionMessage.from(toolExecutionResultMessage.toolName(), toolExecutionResultMessage.text());
            }

            return ToolMessage.from(toolExecutionResultMessage.id(), toolExecutionResultMessage.text());
        }

        throw illegalArgument("Unknown message type: " + message.type());
    }

    private static dev.ai4j.openai4j.chat.Content toOpenAiContent(Content content) {
        if (content instanceof TextContent) {
            return toOpenAiContent((TextContent) content);
        } else if (content instanceof ImageContent) {
            return toOpenAiContent((ImageContent) content);
        } else {
            throw illegalArgument("Unknown content type: " + content);
        }
    }

    private static dev.ai4j.openai4j.chat.Content toOpenAiContent(TextContent content) {
        return dev.ai4j.openai4j.chat.Content.builder()
                .type(ContentType.TEXT)
                .text(content.text())
                .build();
    }

    private static dev.ai4j.openai4j.chat.Content toOpenAiContent(ImageContent content) {
        return dev.ai4j.openai4j.chat.Content.builder()
                .type(ContentType.IMAGE_URL)
                .imageUrl(ImageUrl.builder()
                        .url(toUrl(content.image()))
                        .detail(toDetail(content.detailLevel()))
                        .build())
                .build();
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
        return toolSpecifications.stream()
                .map(InternalOpenAiHelper::toFunction)
                .collect(toList());
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

    private static dev.ai4j.openai4j.chat.JsonObjectSchema toOpenAiParameters(ToolParameters toolParameters, boolean strict) {
        if (toolParameters == null) {
            dev.ai4j.openai4j.chat.JsonObjectSchema.Builder builder = dev.ai4j.openai4j.chat.JsonObjectSchema.builder();
            if (strict) {
                // when strict, additionalProperties must be false:
                // https://platform.openai.com/docs/guides/structured-outputs/additionalproperties-false-must-always-be-set-in-objects
                builder.additionalProperties(false);
            }
            return builder.build();
        }

        dev.ai4j.openai4j.chat.JsonObjectSchema.Builder builder = dev.ai4j.openai4j.chat.JsonObjectSchema.builder()
                .properties(toOpenAiProperties(toolParameters.properties(), strict))
                .required(toolParameters.required());
        if (strict) {
            builder
                    // when strict, all fields must be required:
                    // https://platform.openai.com/docs/guides/structured-outputs/all-fields-must-be-required
                    .required(new ArrayList<>(toolParameters.properties().keySet()))
                    // when strict, additionalProperties must be false:
                    // https://platform.openai.com/docs/guides/structured-outputs/additionalproperties-false-must-always-be-set-in-objects
                    .additionalProperties(false);
        }
        return builder.build();
    }

    private static Map<String, dev.ai4j.openai4j.chat.JsonSchemaElement> toOpenAiProperties(Map<String, ?> properties, boolean strict) {
        Map<String, dev.ai4j.openai4j.chat.JsonSchemaElement> openAiProperties = new LinkedHashMap<>();
        properties.forEach((key, value) ->
                openAiProperties.put(key, toOpenAiJsonSchemaElement((Map<String, ?>) value, strict)));
        return openAiProperties;
    }

    private static dev.ai4j.openai4j.chat.JsonSchemaElement toOpenAiJsonSchemaElement(Map<String, ?> properties, boolean strict) {
        // TODO rewrite when JsonSchemaElement will be used for ToolSpecification.properties
        Object type = properties.get("type");
        String description = (String) properties.get("description");
        if ("object".equals(type)) {
            List<String> required = (List<String>) properties.get("required");
            dev.ai4j.openai4j.chat.JsonObjectSchema.Builder builder = dev.ai4j.openai4j.chat.JsonObjectSchema.builder()
                    .description(description)
                    .properties(toOpenAiProperties((Map<String, ?>) properties.get("properties"), strict));
            if (required != null) {
                builder.required(required);
            }
            if (strict) {
                builder
                        // when strict, all fields must be required:
                        // https://platform.openai.com/docs/guides/structured-outputs/all-fields-must-be-required
                        .required(new ArrayList<>(((Map<String, ?>) properties.get("properties")).keySet()))
                        // when strict, additionalProperties must be false:
                        // https://platform.openai.com/docs/guides/structured-outputs/additionalproperties-false-must-always-be-set-in-objects
                        .additionalProperties(false);
            }
            return builder.build();
        } else if ("array".equals(type)) {
            return dev.ai4j.openai4j.chat.JsonArraySchema.builder()
                    .description(description)
                    .items(toOpenAiJsonSchemaElement((Map<String, ?>) properties.get("items"), strict))
                    .build();
        } else if (properties.get("enum") != null) {
            return dev.ai4j.openai4j.chat.JsonEnumSchema.builder()
                    .description(description)
                    .enumValues((List<String>) properties.get("enum"))
                    .build();
        } else if ("string".equals(type)) {
            return dev.ai4j.openai4j.chat.JsonStringSchema.builder()
                    .description(description)
                    .build();
        } else if ("integer".equals(type)) {
            return dev.ai4j.openai4j.chat.JsonIntegerSchema.builder()
                    .description(description)
                    .build();
        } else if ("number".equals(type)) {
            return dev.ai4j.openai4j.chat.JsonNumberSchema.builder()
                    .description(description)
                    .build();
        } else if ("boolean".equals(type)) {
            return dev.ai4j.openai4j.chat.JsonBooleanSchema.builder()
                    .description(description)
                    .build();
        } else {
            throw new IllegalArgumentException("Unknown type " + type);
        }
    }

    public static AiMessage aiMessageFrom(ChatCompletionResponse response) {
        AssistantMessage assistantMessage = response.choices().get(0).message();
        String text = assistantMessage.content();

        List<ToolCall> toolCalls = assistantMessage.toolCalls();
        if (!isNullOrEmpty(toolCalls)) {
            List<ToolExecutionRequest> toolExecutionRequests = toolCalls.stream()
                    .filter(toolCall -> toolCall.type() == FUNCTION)
                    .map(InternalOpenAiHelper::toToolExecutionRequest)
                    .collect(toList());
            return isNullOrBlank(text) ?
                    AiMessage.from(toolExecutionRequests) :
                    AiMessage.from(text, toolExecutionRequests);
        }

        FunctionCall functionCall = assistantMessage.functionCall();
        if (functionCall != null) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(functionCall.name())
                    .arguments(functionCall.arguments())
                    .build();
            return isNullOrBlank(text) ?
                    AiMessage.from(toolExecutionRequest) :
                    AiMessage.from(text, singletonList(toolExecutionRequest));
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

    public static TokenUsage tokenUsageFrom(Usage openAiUsage) {
        if (openAiUsage == null) {
            return null;
        }
        return new TokenUsage(
                openAiUsage.promptTokens(),
                openAiUsage.completionTokens(),
                openAiUsage.totalTokens()
        );
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

    static boolean isOpenAiModel(String modelName) {
        if (modelName == null) {
            return false;
        }
        for (OpenAiChatModelName openAiChatModelName : OpenAiChatModelName.values()) {
            if (modelName.contains(openAiChatModelName.toString())) {
                return true;
            }
        }
        return false;
    }

    static Response<AiMessage> removeTokenUsage(Response<AiMessage> response) {
        return Response.from(response.content(), null, response.finishReason());
    }

    static ChatModelRequest createModelListenerRequest(ChatCompletionRequest request,
                                                       List<ChatMessage> messages,
                                                       List<ToolSpecification> toolSpecifications) {
        return ChatModelRequest.builder()
                .model(request.model())
                .temperature(request.temperature())
                .topP(request.topP())
                .maxTokens(request.maxTokens())
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
    }

    static ChatModelResponse createModelListenerResponse(String responseId,
                                                         String responseModel,
                                                         Response<AiMessage> response) {
        if (response == null) {
            return null;
        }

        return ChatModelResponse.builder()
                .id(responseId)
                .model(responseModel)
                .tokenUsage(response.tokenUsage())
                .finishReason(response.finishReason())
                .aiMessage(response.content())
                .build();
    }

    static dev.ai4j.openai4j.chat.ResponseFormat toOpenAiResponseFormat(ResponseFormat responseFormat, Boolean strict) {
        if (responseFormat == null || responseFormat.type() == TEXT) {
            return null;
        }

        JsonSchema jsonSchema = responseFormat.jsonSchema();
        if (jsonSchema == null) {
            return dev.ai4j.openai4j.chat.ResponseFormat.builder()
                    .type(JSON_OBJECT)
                    .build();
        } else {
            if (!(jsonSchema.rootElement() instanceof JsonObjectSchema)) {
                throw new IllegalArgumentException("For OpenAI, the root element of the JSON Schema must be a JsonObjectSchema, but it was: " + jsonSchema.rootElement().getClass());
            }
            dev.ai4j.openai4j.chat.JsonSchema openAiJsonSchema = dev.ai4j.openai4j.chat.JsonSchema.builder()
                    .name(jsonSchema.name())
                    .strict(strict)
                    .schema((dev.ai4j.openai4j.chat.JsonObjectSchema) toOpenAiJsonSchemaElement(jsonSchema.rootElement()))
                    .build();
            return dev.ai4j.openai4j.chat.ResponseFormat.builder()
                    .type(JSON_SCHEMA)
                    .jsonSchema(openAiJsonSchema)
                    .build();
        }
    }

    private static dev.ai4j.openai4j.chat.JsonSchemaElement toOpenAiJsonSchemaElement(JsonSchemaElement jsonSchemaElement) {
        if (jsonSchemaElement instanceof JsonStringSchema) {
            return dev.ai4j.openai4j.chat.JsonStringSchema.builder()
                    .description(((JsonStringSchema) jsonSchemaElement).description())
                    .build();
        } else if (jsonSchemaElement instanceof JsonIntegerSchema) {
            return dev.ai4j.openai4j.chat.JsonIntegerSchema.builder()
                    .description(((JsonIntegerSchema) jsonSchemaElement).description())
                    .build();
        } else if (jsonSchemaElement instanceof JsonNumberSchema) {
            return dev.ai4j.openai4j.chat.JsonNumberSchema.builder()
                    .description(((JsonNumberSchema) jsonSchemaElement).description())
                    .build();
        } else if (jsonSchemaElement instanceof JsonBooleanSchema) {
            return dev.ai4j.openai4j.chat.JsonBooleanSchema.builder()
                    .description(((JsonBooleanSchema) jsonSchemaElement).description())
                    .build();
        } else if (jsonSchemaElement instanceof JsonEnumSchema) {
            return dev.ai4j.openai4j.chat.JsonEnumSchema.builder()
                    .description(((JsonEnumSchema) jsonSchemaElement).description())
                    .enumValues(((JsonEnumSchema) jsonSchemaElement).enumValues())
                    .build();
        } else if (jsonSchemaElement instanceof JsonArraySchema) {
            return dev.ai4j.openai4j.chat.JsonArraySchema.builder()
                    .description(((JsonArraySchema) jsonSchemaElement).description())
                    .items(toOpenAiJsonSchemaElement(((JsonArraySchema) jsonSchemaElement).items()))
                    .build();
        } else if (jsonSchemaElement instanceof JsonObjectSchema) {
            Map<String, JsonSchemaElement> properties = ((JsonObjectSchema) jsonSchemaElement).properties();
            Map<String, dev.ai4j.openai4j.chat.JsonSchemaElement> openAiProperties = new LinkedHashMap<>();
            properties.forEach((key, value) -> openAiProperties.put(key, toOpenAiJsonSchemaElement(value)));
            return dev.ai4j.openai4j.chat.JsonObjectSchema.builder()
                    .description(((JsonObjectSchema) jsonSchemaElement).description())
                    .properties(openAiProperties)
                    .required(((JsonObjectSchema) jsonSchemaElement).required())
                    .additionalProperties(((JsonObjectSchema) jsonSchemaElement).additionalProperties())
                    .build();
        } else {
            throw new IllegalArgumentException("Unknown type: " + jsonSchemaElement);
        }
    }
}
