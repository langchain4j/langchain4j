package dev.langchain4j.model.anthropic.internal.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicImageContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMessageContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTextContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTool;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolResultContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolSchema;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolUseContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicUsage;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicRole.ASSISTANT;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicRole.USER;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.OTHER;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class AnthropicMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static List<AnthropicMessage> toAnthropicMessages(List<ChatMessage> messages) {

        List<AnthropicMessage> anthropicMessages = new ArrayList<>();
        List<AnthropicMessageContent> toolContents = new ArrayList<>();

        for (ChatMessage message : messages) {

            if (message instanceof ToolExecutionResultMessage) {
                toolContents.add(toAnthropicToolResultContent((ToolExecutionResultMessage) message));
            } else {
                if (!toolContents.isEmpty()) {
                    anthropicMessages.add(new AnthropicMessage(USER, toolContents));
                    toolContents = new ArrayList<>();
                }

                if (message instanceof UserMessage) {
                    List<AnthropicMessageContent> contents = toAnthropicMessageContents((UserMessage) message);
                    anthropicMessages.add(new AnthropicMessage(USER, contents));
                } else if (message instanceof AiMessage) {
                    List<AnthropicMessageContent> contents = toAnthropicMessageContents((AiMessage) message);
                    anthropicMessages.add(new AnthropicMessage(ASSISTANT, contents));
                }
            }
        }

        if (!toolContents.isEmpty()) {
            anthropicMessages.add(new AnthropicMessage(USER, toolContents));
        }

        return anthropicMessages;
    }

    private static AnthropicToolResultContent toAnthropicToolResultContent(ToolExecutionResultMessage message) {
        return new AnthropicToolResultContent(message.id(), message.text(), null); // TODO propagate isError
    }

    private static List<AnthropicMessageContent> toAnthropicMessageContents(UserMessage message) {
        return message.contents().stream()
                .map(content -> {
                    if (content instanceof TextContent) {
                        return new AnthropicTextContent(((TextContent) content).text());
                    } else if (content instanceof ImageContent) {
                        Image image = ((ImageContent) content).image();
                        if (image.url() != null) {
                            throw illegalArgument("Anthropic does not support images as URLs, " +
                                    "only as Base64-encoded strings");
                        }
                        return new AnthropicImageContent(
                                ensureNotBlank(image.mimeType(), "mimeType"),
                                ensureNotBlank(image.base64Data(), "base64Data")
                        );
                    } else {
                        throw illegalArgument("Unknown content type: " + content);
                    }
                }).collect(toList());
    }

    private static List<AnthropicMessageContent> toAnthropicMessageContents(AiMessage message) {
        List<AnthropicMessageContent> contents = new ArrayList<>();

        if (isNotNullOrBlank(message.text())) {
            contents.add(new AnthropicTextContent(message.text()));
        }

        if (message.hasToolExecutionRequests()) {
            List<AnthropicToolUseContent> toolUseContents = message.toolExecutionRequests().stream()
                    .map(toolExecutionRequest -> {
                        try {
                            return AnthropicToolUseContent.builder()
                                    .id(toolExecutionRequest.id())
                                    .name(toolExecutionRequest.name())
                                    .input(OBJECT_MAPPER.readValue(toolExecutionRequest.arguments(), Map.class))
                                    .build();
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(toList());
            contents.addAll(toolUseContents);
        }

        return contents;
    }

    public static String toAnthropicSystemPrompt(List<ChatMessage> messages) {
        String systemPrompt = messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> ((SystemMessage) message).text())
                .collect(joining("\n\n"));

        if (isNullOrBlank(systemPrompt)) {
            return null;
        } else {
            return systemPrompt;
        }
    }

    public static AiMessage toAiMessage(List<AnthropicContent> contents) {

        String text = contents.stream()
                .filter(content -> "text".equals(content.type))
                .map(content -> content.text)
                .collect(joining("\n"));

        List<ToolExecutionRequest> toolExecutionRequests = contents.stream()
                .filter(content -> "tool_use".equals(content.type))
                .map(content -> {
                    try {
                        return ToolExecutionRequest.builder()
                                .id(content.id)
                                .name(content.name)
                                .arguments(OBJECT_MAPPER.writeValueAsString(content.input))
                                .build();
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(toList());

        if (isNotNullOrBlank(text) && !isNullOrEmpty(toolExecutionRequests)) {
            return new AiMessage(text, toolExecutionRequests);
        } else if (!isNullOrEmpty(toolExecutionRequests)) {
            return AiMessage.from(toolExecutionRequests);
        } else {
            return AiMessage.from(text);
        }
    }

    public static TokenUsage toTokenUsage(AnthropicUsage anthropicUsage) {
        if (anthropicUsage == null) {
            return null;
        }
        return new TokenUsage(anthropicUsage.inputTokens, anthropicUsage.outputTokens);
    }

    public static FinishReason toFinishReason(String anthropicStopReason) {
        if (anthropicStopReason == null) {
            return null;
        }
        switch (anthropicStopReason) {
            case "end_turn":
                return STOP;
            case "max_tokens":
                return LENGTH;
            case "stop_sequence":
                return OTHER; // TODO
            case "tool_use":
                return TOOL_EXECUTION;
            default:
                return null; // TODO
        }
    }

    public static List<AnthropicTool> toAnthropicTools(List<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null) {
            return null;
        }
        return toolSpecifications.stream()
                .map(AnthropicMapper::toAnthropicTool)
                .collect(toList());
    }

    public static AnthropicTool toAnthropicTool(ToolSpecification toolSpecification) {
        if (toolSpecification.parameters() != null) {
            JsonObjectSchema parameters = toolSpecification.parameters();
            return AnthropicTool.builder()
                    .name(toolSpecification.name())
                    .description(toolSpecification.description())
                    .inputSchema(AnthropicToolSchema.builder()
                            .properties(parameters != null ? toAnthropicProperties(parameters.properties()) : emptyMap())
                            .required(parameters != null ? parameters.required() : emptyList())
                            .build())
                    .build();
        } else {
            ToolParameters parameters = toolSpecification.toolParameters();
            return AnthropicTool.builder()
                    .name(toolSpecification.name())
                    .description(toolSpecification.description())
                    .inputSchema(AnthropicToolSchema.builder()
                            .properties(parameters != null ? parameters.properties() : emptyMap())
                            .required(parameters != null ? parameters.required() : emptyList())
                            .build())
                    .build();
        }
    }

    // TODO extract
    private static Map<String, Map<String, Object>> toAnthropicProperties(Map<String, JsonSchemaElement> properties) {
        Map<String, Map<String, Object>> openAiProperties = new LinkedHashMap<>();
        properties.forEach((key, value) -> openAiProperties.put(key, toAnthropicProperties(value)));
        return openAiProperties;
    }

    // TODO extract
    private static Map<String, Object> toAnthropicProperties(JsonSchemaElement jsonSchemaElement) {
        if (jsonSchemaElement instanceof JsonObjectSchema) {
            JsonObjectSchema jsonObjectSchema = (JsonObjectSchema) jsonSchemaElement;
            Map<String, Object> openAiProperties = new LinkedHashMap<>();
            openAiProperties.put("type", "object");
            if (jsonObjectSchema.description() != null) {
                openAiProperties.put("description", jsonObjectSchema.description());
            }
            openAiProperties.put("properties", toAnthropicProperties(jsonObjectSchema.properties()));
            if (jsonObjectSchema.required() != null) {
                openAiProperties.put("required", jsonObjectSchema.required());
            }
            return openAiProperties;
        } else if (jsonSchemaElement instanceof JsonArraySchema) {
            JsonArraySchema jsonArraySchema = (JsonArraySchema) jsonSchemaElement;
            Map<String, Object> openAiProperties = new LinkedHashMap<>();
            openAiProperties.put("type", "array");
            if (jsonArraySchema.description() != null) {
                openAiProperties.put("description", jsonArraySchema.description());
            }
            openAiProperties.put("items", toAnthropicProperties(jsonArraySchema.items()));
            return openAiProperties;
        } else if (jsonSchemaElement instanceof JsonEnumSchema) {
            JsonEnumSchema jsonEnumSchema = (JsonEnumSchema) jsonSchemaElement;
            Map<String, Object> openAiProperties = new LinkedHashMap<>();
            openAiProperties.put("type", "string");
            if (jsonEnumSchema.description() != null) {
                openAiProperties.put("description", jsonEnumSchema.description());
            }
            openAiProperties.put("enum", jsonEnumSchema.enumValues());
            return openAiProperties;
        } else if (jsonSchemaElement instanceof JsonStringSchema) {
            JsonStringSchema jsonStringSchema = (JsonStringSchema) jsonSchemaElement;
            Map<String, Object> openAiProperties = new LinkedHashMap<>();
            openAiProperties.put("type", "string");
            if (jsonStringSchema.description() != null) {
                openAiProperties.put("description", jsonStringSchema.description());
            }
            return openAiProperties;
        } else if (jsonSchemaElement instanceof JsonIntegerSchema) {
            JsonIntegerSchema jsonIntegerSchema = (JsonIntegerSchema) jsonSchemaElement;
            Map<String, Object> openAiProperties = new LinkedHashMap<>();
            openAiProperties.put("type", "integer");
            if (jsonIntegerSchema.description() != null) {
                openAiProperties.put("description", jsonIntegerSchema.description());
            }
            return openAiProperties;
        } else if (jsonSchemaElement instanceof JsonNumberSchema) {
            JsonNumberSchema jsonNumberSchema = (JsonNumberSchema) jsonSchemaElement;
            Map<String, Object> openAiProperties = new LinkedHashMap<>();
            openAiProperties.put("type", "number");
            if (jsonNumberSchema.description() != null) {
                openAiProperties.put("description", jsonNumberSchema.description());
            }
            return openAiProperties;
        } else if (jsonSchemaElement instanceof JsonBooleanSchema) {
            JsonBooleanSchema jsonBooleanSchema = (JsonBooleanSchema) jsonSchemaElement;
            Map<String, Object> openAiProperties = new LinkedHashMap<>();
            openAiProperties.put("type", "boolean");
            if (jsonBooleanSchema.description() != null) {
                openAiProperties.put("description", jsonBooleanSchema.description());
            }
            return openAiProperties;
        } else {
            throw new IllegalArgumentException("Unknown type: " + jsonSchemaElement.getClass());
        }
    }
}
