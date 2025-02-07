package dev.langchain4j.model.openaiofficial;

import com.openai.core.JsonValue;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionAssistantMessageParam;
import com.openai.models.ChatCompletionContentPart;
import com.openai.models.ChatCompletionContentPartImage;
import com.openai.models.ChatCompletionContentPartInputAudio;
import com.openai.models.ChatCompletionContentPartText;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatCompletionMessage;
import com.openai.models.ChatCompletionMessageParam;
import com.openai.models.ChatCompletionMessageToolCall;
import com.openai.models.ChatCompletionReasoningEffort;
import com.openai.models.ChatCompletionSystemMessageParam;
import com.openai.models.ChatCompletionTool;
import com.openai.models.ChatCompletionToolChoiceOption;
import com.openai.models.ChatCompletionToolMessageParam;
import com.openai.models.ChatCompletionUserMessageParam;
import com.openai.models.CompletionUsage;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.Metadata;
import com.openai.models.ResponseFormatJsonObject;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.chat.request.ResponseFormat.JSON;
import static dev.langchain4j.model.chat.request.ResponseFormatType.TEXT;
import static java.util.stream.Collectors.toList;

public class InternalOpenAiOfficialHelper {

    static final String OPENAI_URL = "https://api.openai.com/v1";

    static final String OPENAI_DEMO_API_KEY = "demo";
    static final String OPENAI_DEMO_URL = "http://langchain4j.dev/demo/openai/v1";

    static final String DEFAULT_USER_AGENT = "langchain4j-openai-official";

    public static List<ChatCompletionMessageParam> toOpenAiMessages(List<ChatMessage> messages) {
        return messages.stream().map(InternalOpenAiOfficialHelper::toOpenAiMessage).collect(toList());
    }

    public static ChatCompletionMessageParam toOpenAiMessage(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return ChatCompletionMessageParam.ofSystem(
                    ChatCompletionSystemMessageParam.builder()
                            .content(systemMessage.text()).build());
        }

        if (message instanceof UserMessage userMessage) {
            final ChatCompletionUserMessageParam.Builder builder = ChatCompletionUserMessageParam.builder();
            if (userMessage.hasSingleText()) {
                builder.content(userMessage.singleText());
            } else {
                builder.contentOfArrayOfContentParts(toOpenAiContent(userMessage.contents()));
            }
            if (userMessage.name() != null) {
                builder.name(userMessage.name());
            }
            return ChatCompletionMessageParam.ofUser(builder.build());
        }

        if (message instanceof AiMessage aiMessage) {

            if (!aiMessage.hasToolExecutionRequests()) {
                return ChatCompletionMessageParam.ofAssistant(ChatCompletionAssistantMessageParam.builder()
                        .content(aiMessage.text())
                        .build());
            }

            ToolExecutionRequest toolExecutionRequest =
                    aiMessage.toolExecutionRequests().get(0);
            if (toolExecutionRequest.id() == null) {

                return ChatCompletionMessageParam.ofAssistant(ChatCompletionAssistantMessageParam.builder()
                        .addToolCall(
                                ChatCompletionMessageToolCall.builder()
                                        .function(ChatCompletionMessageToolCall.Function.builder()
                                                .name(toolExecutionRequest.name())
                                                .arguments(toolExecutionRequest.arguments())
                                                .build())
                                        .build())
                        .build());
            }

            List<ChatCompletionMessageToolCall> toolCalls = aiMessage.toolExecutionRequests().stream()
                    .map(it -> ChatCompletionMessageToolCall.builder()
                            .id(it.id())
                            .function(ChatCompletionMessageToolCall.Function.builder()
                                    .name(it.name())
                                    .arguments(it.arguments())
                                    .build())
                            .build())
                    .collect(toList());

            return ChatCompletionMessageParam.ofAssistant(ChatCompletionAssistantMessageParam.builder()
                    .content(aiMessage.text() != null ? aiMessage.text() : "")
                    .toolCalls(toolCalls)
                    .build());
        }

        if (message instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
                return ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam.builder()
                        .toolCallId(toolExecutionResultMessage.id())
                        .content(toolExecutionResultMessage.text())
                        .build());
        }

        throw illegalArgument("Unknown message type: " + message.type());
    }

    private static List<ChatCompletionContentPart> toOpenAiContent(List<Content> contents) {
        List<ChatCompletionContentPart> parts = new ArrayList<>();
        for (Content content : contents) {
            if (content instanceof TextContent textContent) {
                parts.add(ChatCompletionContentPart.ofText(
                        ChatCompletionContentPartText.builder()
                                .text(textContent.text())
                                .build()
                                ));
            } else if (content instanceof ImageContent imageContent) {
                if (imageContent.image().url() == null) {
                    throw new UnsupportedFeatureException("Image URL is not present. " +
                            "Base64 encoded images are not supported at the moment.");
                }
                ChatCompletionContentPartImage.ImageUrl.Builder imageUrlBuilder = ChatCompletionContentPartImage.ImageUrl.builder();
                parts.add(ChatCompletionContentPart.ofImageUrl(
                        ChatCompletionContentPartImage.builder()
                                .imageUrl(imageUrlBuilder.build())
                                .build()));
            } else if (content instanceof AudioContent audioContent) {
                parts.add(ChatCompletionContentPart.ofInputAudio(
                        ChatCompletionContentPartInputAudio.builder()
                                .inputAudio(
                                        ChatCompletionContentPartInputAudio.builder()
                                                .inputAudio(
                                                        ChatCompletionContentPartInputAudio.InputAudio.builder()
                                                                .data(ensureNotBlank(audioContent.audio().base64Data(), "audio.base64Data"))
                                                                .build())
                                                .build().inputAudio())
                                .build()
                ));
            } else {
                throw illegalArgument("Unknown content type: " + content);
            }
        }
        return parts;
    }

    public static List<ChatCompletionTool> toTools(Collection<ToolSpecification> toolSpecifications, boolean strict) {
        if (toolSpecifications == null) {
            return null;
        }

        return toolSpecifications.stream()
                .map((ToolSpecification toolSpecification) -> toTool(toolSpecification, strict))
                .collect(toList());
    }

    private static ChatCompletionTool toTool(ToolSpecification toolSpecification, boolean strict) {

        FunctionDefinition.Builder functionDefinitionBuilder = FunctionDefinition.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description() != null ? toolSpecification.description() : "")
                .parameters(toOpenAiParameters(toolSpecification, strict));

        if (strict) {
            functionDefinitionBuilder.strict(true);
        }

        return ChatCompletionTool.builder()
                .function(functionDefinitionBuilder.build()).build();
    }

    private static FunctionParameters toOpenAiParameters(
            ToolSpecification toolSpecification, boolean strict) {

        FunctionParameters.Builder parametersBuilder = FunctionParameters.builder();

        JsonObjectSchema parameters = toolSpecification.parameters();
        parametersBuilder.putAdditionalProperty("type", JsonValue.from("object"));

        if (parameters != null) {
            parametersBuilder.putAdditionalProperty("properties", toOpenAiProperties(parameters.properties(), strict));

            if (strict) {
                // when strict, all fields must be required:
                // https://platform.openai.com/docs/guides/structured-outputs/all-fields-must-be-required
                parametersBuilder.putAdditionalProperty("required", JsonValue.from(new ArrayList<>(parameters.properties().keySet())));
                // when strict, additionalProperties must be false:
                // https://platform.openai.com/docs/guides/structured-outputs/additionalproperties-false-must-always-be-set-in-objects
                parametersBuilder.putAdditionalProperty("additionalProperties", JsonValue.from(false));
            } else {
                parametersBuilder.putAdditionalProperty("required", JsonValue.from(parameters.required()));
                parametersBuilder.putAdditionalProperty("additionalProperties", JsonValue.from(false));
            }
            return parametersBuilder.build();
        } else {
            if (strict) {
                parametersBuilder.putAdditionalProperty("additionalProperties", JsonValue.from(false));
            }
            return parametersBuilder.build();
        }
    }

    private static JsonValue toOpenAiProperties(
            Map<String, JsonSchemaElement> properties, boolean strict) {

        if (properties == null) {
            return null;
        }

        return JsonValue.from(properties.keySet().stream()
                .collect(Collectors.toMap(
                        key -> key,
                        key -> toOpenAiJsonSchemaElement(properties.get(key), strict))));
    }

    private static Map<String, Object> toOpenAiJsonSchemaElement(
            JsonSchemaElement jsonSchemaElement, boolean strict) {

        if (jsonSchemaElement instanceof JsonObjectSchema jsonObjectSchema) {

            Map<String, Object> objectMap =  new HashMap<>();
            objectMap.put("type", "object");
            objectMap.put("description", JsonValue.from(jsonObjectSchema.description() != null ? jsonObjectSchema.description() : ""));
            objectMap.put("properties", toOpenAiProperties(jsonObjectSchema.properties(), strict));
            objectMap.put("$defs", toOpenAiProperties(jsonObjectSchema.definitions(), strict));
            if (strict) {
                objectMap.put("required", JsonValue.from(new ArrayList<>(jsonObjectSchema.properties().keySet())));
                objectMap.put("additionalProperties", JsonValue.from(false));
            } else {
                objectMap.put("additionalProperties", jsonObjectSchema.additionalProperties());
                if (jsonObjectSchema.required() != null) {
                    objectMap.put("required", JsonValue.from(jsonObjectSchema.required()));
                }
            }

            return objectMap;
        } else if (jsonSchemaElement instanceof JsonArraySchema jsonArraySchema) {
            return Map.of("type",
                    "array",
                    "description",
                    JsonValue.from(jsonArraySchema.description() != null ? jsonArraySchema.description() : ""),
                    "items",
                    JsonValue.from(toOpenAiJsonSchemaElement(jsonArraySchema.items(), strict)));
        } else if (jsonSchemaElement instanceof JsonEnumSchema jsonEnumSchema) {
            return Map.of("type",
                    "string",
                    "description",
                    JsonValue.from(jsonEnumSchema.description() != null ? jsonEnumSchema.description() : ""),
                    "enum",
                    JsonValue.from(jsonEnumSchema.enumValues()));
        } else if (jsonSchemaElement instanceof JsonStringSchema jsonStringSchema) {
            return Map.of("type",
                    "string",
                    "description",
                    JsonValue.from(jsonStringSchema.description() != null ? jsonStringSchema.description() : ""));
        } else if (jsonSchemaElement instanceof JsonIntegerSchema jsonIntegerSchema) {
            return Map.of("type",
                    "integer",
                    "description",
                    JsonValue.from(jsonIntegerSchema.description() != null ? jsonIntegerSchema.description() : ""));
        } else if (jsonSchemaElement instanceof JsonNumberSchema jsonNumberSchema) {
            return Map.of("type",
                    "number",
                    "description",
                    JsonValue.from(jsonNumberSchema.description() != null ? jsonNumberSchema.description() : ""));
        } else if (jsonSchemaElement instanceof JsonBooleanSchema jsonBooleanSchema) {
            return Map.of("type",
                    "boolean",
                    "description",
                    JsonValue.from(jsonBooleanSchema.description() != null ? jsonBooleanSchema.description() : ""));
        } else if (jsonSchemaElement instanceof JsonReferenceSchema jsonReferenceSchema) {
            return Map.of("type",
                    "",
                    "$ref",
                    "#/$defs/" + jsonReferenceSchema.reference());
        } else if (jsonSchemaElement instanceof JsonAnyOfSchema jsonAnyOfSchema) {
            return Map.of("type",
                    "anyOf",
                    "description",
                    JsonValue.from(jsonAnyOfSchema.description() != null ? jsonAnyOfSchema.description() : ""),
                    "anyOf",
                    JsonValue.from(jsonAnyOfSchema.anyOf().stream()
                            .map(it -> toOpenAiJsonSchemaElement(it, strict))
                            .collect(toList())));
        } else {
            throw new IllegalArgumentException("Unknown type: " + jsonSchemaElement.getClass());
        }
    }

    public static AiMessage aiMessageFrom(ChatCompletion chatCompletion) {
        ChatCompletionMessage assistantMessage = chatCompletion.choices().get(0).message();
        Optional<String> text = assistantMessage.content();

        Optional<List<ChatCompletionMessageToolCall>> toolCalls = assistantMessage.toolCalls();
        if (toolCalls.isPresent()) {
            List<ToolExecutionRequest> toolExecutionRequests = toolCalls.get().stream()
                    .map(InternalOpenAiOfficialHelper::toToolExecutionRequest)
                    .collect(toList());

            if (text.isEmpty()) {
                return AiMessage.from(toolExecutionRequests);
            } else if (toolExecutionRequests.isEmpty()) {
                return AiMessage.from(text.get());
            } else {
                return AiMessage.from(text.get(), toolExecutionRequests);
            }
        }

        return AiMessage.from(text.orElse(""));
    }

    private static ToolExecutionRequest toToolExecutionRequest(ChatCompletionMessageToolCall toolCall) {
        ChatCompletionMessageToolCall.Function function = toolCall.function();
        return ToolExecutionRequest.builder()
                .id(toolCall.id())
                .name(function.name())
                .arguments(function.arguments())
                .build();
    }

    public static OpenAiOfficialTokenUsage tokenUsageFrom(Optional<CompletionUsage> openAiUsage) {
        if (openAiUsage.isEmpty()) {
            return null;
        }

        Optional<CompletionUsage.PromptTokensDetails> promptTokensDetails = openAiUsage.get().promptTokensDetails();
        OpenAiOfficialTokenUsage.InputTokensDetails inputTokensDetails = null;
        if (promptTokensDetails.isPresent() && promptTokensDetails.get().cachedTokens().isPresent()) {
            inputTokensDetails = new OpenAiOfficialTokenUsage.InputTokensDetails(promptTokensDetails.get().cachedTokens().get());
        }

        Optional<CompletionUsage.CompletionTokensDetails> completionTokensDetails = openAiUsage.get().completionTokensDetails();
        OpenAiOfficialTokenUsage.OutputTokensDetails outputTokensDetails = null;
        if (completionTokensDetails.isPresent() && completionTokensDetails.get().reasoningTokens().isPresent()) {
            outputTokensDetails = new OpenAiOfficialTokenUsage.OutputTokensDetails(completionTokensDetails.get().reasoningTokens().get());
        }

        return OpenAiOfficialTokenUsage.builder()
                .inputTokenCount(openAiUsage.get().promptTokens())
                .inputTokensDetails(inputTokensDetails)
                .outputTokenCount(openAiUsage.get().completionTokens())
                .outputTokensDetails(outputTokensDetails)
                .totalTokenCount(openAiUsage.get().totalTokens())
                .build();
    }

    public static FinishReason finishReasonFrom(ChatCompletion.Choice.FinishReason openAiFinishReason) {
        if (openAiFinishReason == null) {
            return null;
        }
        if (openAiFinishReason.equals(ChatCompletion.Choice.FinishReason.STOP)) {
            return FinishReason.STOP;
        } else if (openAiFinishReason.equals(ChatCompletion.Choice.FinishReason.LENGTH)) {
            return FinishReason.LENGTH;
        } else if (openAiFinishReason.equals(ChatCompletion.Choice.FinishReason.TOOL_CALLS)) {
            return FinishReason.TOOL_EXECUTION;
        } else if (openAiFinishReason.equals(ChatCompletion.Choice.FinishReason.FUNCTION_CALL)) {
            return FinishReason.TOOL_EXECUTION;
        } else if (openAiFinishReason.equals(ChatCompletion.Choice.FinishReason.CONTENT_FILTER)) {
            return FinishReason.CONTENT_FILTER;
        } else {
            return null;
        }
    }

    static ResponseFormatJsonObject toOpenAiResponseFormat(ResponseFormat responseFormat, Boolean strict) {
        if (responseFormat == null || responseFormat.type() == TEXT) {
            return null;
        }

        JsonSchema jsonSchema = responseFormat.jsonSchema();
        if (jsonSchema == null) {
            return ResponseFormatJsonObject.builder()
                    .type(JsonValue.from("json_object"))
                    .build();
        } else {
            if (!(jsonSchema.rootElement() instanceof JsonObjectSchema)) {
                throw new IllegalArgumentException(
                        "For OpenAI, the root element of the JSON Schema must be a JsonObjectSchema, but it was: "
                                + jsonSchema.rootElement().getClass());
            }
            Map<String, JsonValue> properties = new HashMap<>();
            properties.put("name", JsonValue.from(jsonSchema.name()));
            properties.put("strict", strict ? JsonValue.from(true) : JsonValue.from(false));
            properties.put("schema", JsonValue.from(toOpenAiJsonSchemaElement(jsonSchema.rootElement(), strict)));

            return ResponseFormatJsonObject.builder()
                    .type(JsonValue.from("json_schema"))
                    .putAllAdditionalProperties(Map.of("json_schema", JsonValue.from(properties)))
                    .build();
        }
    }

    public static ChatCompletionToolChoiceOption toOpenAiToolChoice(ToolChoice toolChoice) {
        if (toolChoice == null) {
            return null;
        }

        return switch (toolChoice) {
            case AUTO -> ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.AUTO);
            case REQUIRED -> ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.REQUIRED);
        };
    }

    public static Response<AiMessage> convertResponse(ChatResponse chatResponse) {
        return Response.from(
                chatResponse.aiMessage(),
                chatResponse.metadata().tokenUsage(),
                chatResponse.metadata().finishReason());
    }

    static void validate(ChatRequestParameters parameters) {
        if (parameters.topK() != null) {
            throw new UnsupportedFeatureException("'topK' parameter is not supported by OpenAI");
        }
    }

    static ResponseFormat fromOpenAiResponseFormat(String responseFormat) {
        if ("json_object".equals(responseFormat)) {
            return JSON;
        } else {
            return null;
        }
    }

    static ChatCompletionCreateParams.Builder toOpenAiChatCompletionCreateParams(
            ChatRequest chatRequest,
            OpenAiOfficialChatRequestParameters parameters,
            Boolean strictTools,
            Boolean strictJsonSchema) {

        // OpenAI-specific parameters
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(parameters.modelName());

        if (parameters.maxOutputTokens() != null && parameters.maxCompletionTokens() == null) {
            builder.maxTokens(parameters.maxOutputTokens());
        }
        if (parameters.maxCompletionTokens() != null) {
            builder.maxCompletionTokens(parameters.maxCompletionTokens());
        }

        if (parameters.logitBias() != null) {
            builder.logitBias(ChatCompletionCreateParams
                    .LogitBias.builder()
                    .putAllAdditionalProperties(parameters.logitBias().entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey, entry -> JsonValue.from(entry.getValue()))))
                    .build());
        }

        if ((parameters.parallelToolCalls() != null)) {
            builder.parallelToolCalls(parameters.parallelToolCalls());
        }

        if (parameters.seed() != null) {
            builder.seed(parameters.seed());
        }

        if (parameters.user() != null) {
            builder.user(parameters.user());
        }

        if (parameters.store() != null) {
            builder.store(parameters.store());
        }

        if (parameters.metadata() != null) {
            builder.metadata(Metadata.builder()
                    .putAllAdditionalProperties(parameters.metadata().entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey, entry -> JsonValue.from(entry.getValue()))))
                    .build());
        }

        if (parameters.serviceTier() != null) {
            builder.serviceTier(ChatCompletionCreateParams.ServiceTier.of(parameters.serviceTier()));
        }

        if (parameters.reasoningEffort() != null) {
            builder.reasoningEffort(ChatCompletionReasoningEffort.of(parameters.reasoningEffort()));
        }

        // Request parameters
        builder.messages(toOpenAiMessages(chatRequest.messages()));

        if (parameters.temperature() != null) {
            builder.temperature(parameters.temperature());
        }

        if (parameters.topP() != null) {
            builder.topP(parameters.topP());
        }

        if (parameters.frequencyPenalty() != null) {
            builder.frequencyPenalty(parameters.frequencyPenalty());
        }

        if (parameters.presencePenalty() != null) {
            builder.presencePenalty(parameters.presencePenalty());
        }

        if (parameters.stopSequences() != null) {
            builder.stop(ChatCompletionCreateParams.Stop.ofStrings(parameters.stopSequences()));
        }

        if (parameters.toolSpecifications() != null) {
            builder.tools(toTools(parameters.toolSpecifications(), strictTools));
        }

        if (parameters.toolChoice() != null) {
            builder.toolChoice(toOpenAiToolChoice(parameters.toolChoice()));
        }

        if (parameters.responseFormat() != null) {
            builder.responseFormat(toOpenAiResponseFormat(parameters.responseFormat(), strictJsonSchema));
        }
        return builder;
    }
}
