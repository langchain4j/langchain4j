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
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.Collection;
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
        if (message instanceof SystemMessage) {
            ChatCompletionMessageParam.ofSystem(
                    ChatCompletionSystemMessageParam.builder()
                            .content(((SystemMessage) message).text()).build());
        }

        if (message instanceof UserMessage userMessage) {

            if (userMessage.hasSingleText()) {
                return ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder()
                                .content(userMessage.singleText())
                                .name(userMessage.name())
                                .build());
            } else {
                return ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder()
                                .contentOfArrayOfContentParts(toOpenAiContent(userMessage.contents()))
                                .name(userMessage.name())
                                .build());
            }
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
                    .content(aiMessage.text())
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
                                .text((textContent).text())
                                .build()
                                ));
            } else if (content instanceof ImageContent imageContent) {
                parts.add(ChatCompletionContentPart.ofImageUrl(
                        ChatCompletionContentPartImage.builder()
                                .imageUrl(
                                        ChatCompletionContentPartImage.ImageUrl.builder()
                                                .url((imageContent).image().url().toString())
                                                .build())
                                .build()
                ));
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

    /*

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
    */

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
                                .description(toolSpecification.description())
                                        .parameters(toOpenAiParameters(toolSpecification, strict));

        if (strict) {
            functionDefinitionBuilder.strict(true);
        }

        return ChatCompletionTool.builder()
                .function(functionDefinitionBuilder.build()).build();
    }

    private static FunctionParameters toOpenAiParameters(
            ToolSpecification toolSpecification, boolean strict) {

        // TODO NOT IMPLEMENTED YET
        return FunctionParameters.builder().build();

    }
    /*

    private static dev.ai4j.openai4j.chat.JsonObjectSchema toOpenAiParameters(
            ToolSpecification toolSpecification, boolean strict) {

        JsonObjectSchema parameters = toolSpecification.parameters();
        if (parameters != null) {
            dev.ai4j.openai4j.chat.JsonObjectSchema.Builder builder = dev.ai4j.openai4j.chat.JsonObjectSchema.builder()
                    .properties(toOpenAiProperties(parameters.properties(), strict))
                    .required(parameters.required())
                    .definitions(toOpenAiProperties(parameters.definitions(), strict));
            if (strict) {
                builder
                        // when strict, all fields must be required:
                        // https://platform.openai.com/docs/guides/structured-outputs/all-fields-must-be-required
                        .required(new ArrayList<>(parameters.properties().keySet()))
                        // when strict, additionalProperties must be false:
                        // https://platform.openai.com/docs/guides/structured-outputs/additionalproperties-false-must-always-be-set-in-objects
                        .additionalProperties(false);
            }
            return builder.build();
        }

        // keeping old logic with ToolParameters for backward compatibility

        ToolParameters toolParameters = toolSpecification.toolParameters();
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
                .properties(toOpenAiPropertiesOld(toolParameters.properties(), strict))
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

    private static Map<String, dev.ai4j.openai4j.chat.JsonSchemaElement> toOpenAiProperties(
            Map<String, JsonSchemaElement> properties, boolean strict) {

        if (properties == null) {
            return null;
        }

        Map<String, dev.ai4j.openai4j.chat.JsonSchemaElement> openAiProperties = new LinkedHashMap<>();
        properties.forEach((key, value) -> openAiProperties.put(key, toOpenAiJsonSchemaElement(value, strict)));
        return openAiProperties;
    }

    private static dev.ai4j.openai4j.chat.JsonSchemaElement toOpenAiJsonSchemaElement(
            JsonSchemaElement jsonSchemaElement, boolean strict) {

        if (jsonSchemaElement instanceof JsonObjectSchema jsonObjectSchema) {
            dev.ai4j.openai4j.chat.JsonObjectSchema.Builder builder = dev.ai4j.openai4j.chat.JsonObjectSchema.builder()
                    .description(jsonObjectSchema.description())
                    .properties(toOpenAiProperties(jsonObjectSchema.properties(), strict))
                    .additionalProperties(strict ? Boolean.FALSE : jsonObjectSchema.additionalProperties())
                    .definitions(toOpenAiProperties(jsonObjectSchema.definitions(), strict));
            if (jsonObjectSchema.required() != null) {
                builder.required(jsonObjectSchema.required());
            }
            if (strict) {
                builder
                        // when strict, all fields must be required:
                        // https://platform.openai.com/docs/guides/structured-outputs/all-fields-must-be-required
                        .required(new ArrayList<>(jsonObjectSchema.properties().keySet()))
                        // when strict, additionalProperties must be false:
                        // https://platform.openai.com/docs/guides/structured-outputs/additionalproperties-false-must-always-be-set-in-objects
                        .additionalProperties(false);
            }
            return builder.build();
        } else if (jsonSchemaElement instanceof JsonArraySchema jsonArraySchema) {
            return dev.ai4j.openai4j.chat.JsonArraySchema.builder()
                    .description(jsonArraySchema.description())
                    .items(toOpenAiJsonSchemaElement(jsonArraySchema.items(), strict))
                    .build();
        } else if (jsonSchemaElement instanceof JsonEnumSchema jsonEnumSchema) {
            return dev.ai4j.openai4j.chat.JsonEnumSchema.builder()
                    .description(jsonEnumSchema.description())
                    .enumValues(jsonEnumSchema.enumValues())
                    .build();
        } else if (jsonSchemaElement instanceof JsonStringSchema jsonStringSchema) {
            return dev.ai4j.openai4j.chat.JsonStringSchema.builder()
                    .description(jsonStringSchema.description())
                    .build();
        } else if (jsonSchemaElement instanceof JsonIntegerSchema jsonIntegerSchema) {
            return dev.ai4j.openai4j.chat.JsonIntegerSchema.builder()
                    .description(jsonIntegerSchema.description())
                    .build();
        } else if (jsonSchemaElement instanceof JsonNumberSchema jsonNumberSchema) {
            return dev.ai4j.openai4j.chat.JsonNumberSchema.builder()
                    .description(jsonNumberSchema.description())
                    .build();
        } else if (jsonSchemaElement instanceof JsonBooleanSchema jsonBooleanSchema) {
            return dev.ai4j.openai4j.chat.JsonBooleanSchema.builder()
                    .description(jsonBooleanSchema.description())
                    .build();
        } else if (jsonSchemaElement instanceof JsonReferenceSchema jsonReferenceSchema) {
            return dev.ai4j.openai4j.chat.JsonReferenceSchema.builder()
                    .reference("#/$defs/" + jsonReferenceSchema.reference())
                    .build();
        } else if (jsonSchemaElement instanceof JsonAnyOfSchema) {
            JsonAnyOfSchema jsonAnyOfSchema = (JsonAnyOfSchema) jsonSchemaElement;
            return dev.ai4j.openai4j.chat.JsonAnyOfSchema.builder()
                    .description(jsonAnyOfSchema.description())
                    .anyOf(jsonAnyOfSchema.anyOf().stream()
                            .map(it -> toOpenAiJsonSchemaElement(it, strict))
                            .collect(toList()))
                    .build();
        } else {
            throw new IllegalArgumentException("Unknown type: " + jsonSchemaElement.getClass());
        }
    }

    */

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
            } else {
                return AiMessage.from(text.get(), toolExecutionRequests);
            }
        }

        return AiMessage.from(text.isPresent() ? text.get() : "");
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
        if (promptTokensDetails != null) {
            inputTokensDetails = new OpenAiOfficialTokenUsage.InputTokensDetails(promptTokensDetails.get().cachedTokens().get());
        }

        Optional<CompletionUsage.CompletionTokensDetails> completionTokensDetails = openAiUsage.get().completionTokensDetails();
        OpenAiOfficialTokenUsage.OutputTokensDetails outputTokensDetails = null;
        if (completionTokensDetails != null) {
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
        // TODO NOT IMPLEMENTED YET
        return ResponseFormatJsonObject.builder().build();
    }

        /*
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
                throw new IllegalArgumentException(
                        "For OpenAI, the root element of the JSON Schema must be a JsonObjectSchema, but it was: "
                                + jsonSchema.rootElement().getClass());
            }
            dev.ai4j.openai4j.chat.JsonSchema openAiJsonSchema = dev.ai4j.openai4j.chat.JsonSchema.builder()
                    .name(jsonSchema.name())
                    .strict(strict)
                    .schema((dev.ai4j.openai4j.chat.JsonObjectSchema)
                            toOpenAiJsonSchemaElement(jsonSchema.rootElement(), strict))
                    .build();
            return dev.ai4j.openai4j.chat.ResponseFormat.builder()
                    .type(JSON_SCHEMA)
                    .jsonSchema(openAiJsonSchema)
                    .build();
        }
    }
    */

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
/*
    static StreamingChatResponseHandler convertHandler(StreamingResponseHandler<AiMessage> handler) {
        return new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onNext(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                handler.onComplete(convertResponse(completeResponse));
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };
    }
    */

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
        builder
                .messages(toOpenAiMessages(chatRequest.messages()))
                .temperature(parameters.temperature())
                .topP(parameters.topP())
                .frequencyPenalty(parameters.frequencyPenalty())
                .presencePenalty(parameters.presencePenalty())
                .maxCompletionTokens(parameters.maxOutputTokens())
                .stop(ChatCompletionCreateParams.Stop.ofStrings(parameters.stopSequences()))
                .tools(toTools(parameters.toolSpecifications(), strictTools))
                .toolChoice(toOpenAiToolChoice(parameters.toolChoice()))
                .responseFormat(toOpenAiResponseFormat(parameters.responseFormat(), strictJsonSchema));

        return builder;
    }
}
