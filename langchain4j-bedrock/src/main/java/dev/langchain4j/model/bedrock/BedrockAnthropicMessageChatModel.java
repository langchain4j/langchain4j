package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.stream.Collectors.joining;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static java.util.Collections.emptyList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.bedrock.internal.Json;
import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Getter
@SuperBuilder
public class BedrockAnthropicMessageChatModel extends AbstractBedrockChatModel<BedrockAnthropicMessageChatModelResponse> {

    private static final String DEFAULT_ANTHROPIC_VERSION = "bedrock-2023-05-31";

    @Builder.Default
    private final int topK = 250;
    @Builder.Default
    private final String anthropicVersion = DEFAULT_ANTHROPIC_VERSION;
    @Builder.Default
    private final String model = Types.AnthropicClaude3SonnetV1.getValue();

    @Override
    protected String getModelId() {
        return model;
    }

    @Override
    protected Map<String, Object> getRequestParameters(String prompt) {
        final Map<String, Object> parameters = new HashMap<>(9);
        parameters.put("max_tokens", getMaxTokens());
        parameters.put("temperature", getTemperature());
        parameters.put("top_k", topK);
        parameters.put("top_p", getTopP());
        parameters.put("stop_sequences", getStopSequences());
        parameters.put("anthropic_version", anthropicVersion);
        return parameters;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, emptyList());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generate(messages, null, toolSpecifications);
    }

    private Response<AiMessage> generate(
            List<ChatMessage> messages,
            ToolSpecification toolChoiceSpecification,
            List<ToolSpecification> toolSpecifications
    ) {
        final String system = getAnthropicSystemPrompt(messages);

        List<BedrockAnthropicMessage> formattedMessages = getAnthropicMessages(messages);

        Map<String, Object> parameters = getRequestParameters(null);
        parameters.put("messages", formattedMessages);
        parameters.put("system", system);

        if (!toolSpecifications.isEmpty()) {
            validateModelIdWithToolsSupport();
            parameters.put("tools", toAnthropicToolSpecifications(toolSpecifications));
        }

        final String body = Json.toJson(parameters);

        InvokeModelResponse invokeModelResponse = withRetry(() -> invoke(body), getMaxRetries());
        final String response = invokeModelResponse.body().asUtf8String();
        BedrockAnthropicMessageChatModelResponse result = Json.fromJson(response, getResponseClassType());

        return new Response<>(
            aiMessageFrom(result),
            result.getTokenUsage(),
            result.getFinishReason()
        );
    }

    private void validateModelIdWithToolsSupport() {
        List<String> anthropicModelIdSplit = Arrays.asList(this.model.split("-"));

        if (anthropicModelIdSplit.size() < 2) {
            throw new IllegalArgumentException("Tools are currently not supported by this model");
        }

        String anthropicModelMajorVersion = anthropicModelIdSplit.get(1);
        if (anthropicModelMajorVersion.contains(":")) {
            anthropicModelMajorVersion = anthropicModelMajorVersion.split(":")[0];
        }

        if (!anthropicModelMajorVersion.matches("[0-9]")) {
            anthropicModelMajorVersion = anthropicModelMajorVersion.replaceAll("[^0-9]", "");
        }

        if (anthropicModelMajorVersion.isEmpty() || Integer.parseInt(anthropicModelMajorVersion) < 3) {
            throw new IllegalArgumentException("Tools are currently not supported by this model");
        }
    }

    private AiMessage aiMessageFrom(BedrockAnthropicMessageChatModelResponse result) {
        List<BedrockAnthropicContent> toolUseRequests = result.getContent()
                .stream()
                .filter(content -> content.getType().equals("tool_use"))
                .collect(Collectors.toList());

        if (toolUseRequests.isEmpty()) {
            return AiMessage.from(result.getOutputText());
        }

        List<ToolExecutionRequest> toolExecutionRequests = toolUseRequests.stream()
                .map(toolUseRequest -> ToolExecutionRequest.builder()
                        .id(toolUseRequest.getId())
                        .name(toolUseRequest.getName())
                        .arguments(Json.toJson(toolUseRequest.getInput()))
                        .build())
                .collect(Collectors.toList());

        return AiMessage.from(toolExecutionRequests);
    }

    private Object toAnthropicToolSpecifications(List<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(toolSpecification -> BedrockAntropicToolSpecification.builder()
                        .name(toolSpecification.name())
                        .description(toolSpecification.description())
                        .input_schema(toAnthropicToolParameters(toolSpecification.parameters()))
                        .build())
                .collect(Collectors.toList());
    }

    private Object toAnthropicToolParameters(ToolParameters toolParameters) {
        if (toolParameters == null) {
            ObjectNode inputSchemaNode = new ObjectMapper().createObjectNode();
            inputSchemaNode.put("type", "object");
            inputSchemaNode.set("properties", new ObjectMapper().createObjectNode());
            inputSchemaNode.set("required", new ObjectMapper().createArrayNode());

            return inputSchemaNode;
        }

        ObjectNode propertiesNode = new ObjectMapper().createObjectNode();
        if (toolParameters.properties() != null) {
            propertiesNode.setAll(toAnthropicParameterProperties(toolParameters.properties()));
        }

        ArrayNode requiredNode = new ObjectMapper().createArrayNode();
        if (toolParameters.required() != null) {
            toolParameters.required().forEach(requiredNode::add);
        }

        ObjectNode inputSchemaNode = new ObjectMapper().createObjectNode();
        inputSchemaNode.put("type", "object");
        inputSchemaNode.set("properties", propertiesNode);
        inputSchemaNode.set("required", requiredNode);

        return inputSchemaNode;
    }

    private ObjectNode toAnthropicParameterProperties(Map<String, Map<String, Object>> properties) {
        ObjectNode propertiesNode = new ObjectMapper().createObjectNode();
        properties.forEach((propertyName, propertyMetadata) ->
                propertiesNode.set(propertyName, toAnthropicParameter(propertyMetadata)));
        return propertiesNode;
    }

    private JsonNode toAnthropicParameter(Map<String, Object> propertyMetadata) {
        ObjectNode propertyNode = new ObjectMapper().createObjectNode();

        String propertyType = propertyMetadata.get("type").toString();
        String propertyDescription = (String) propertyMetadata.get("description");
        if (propertyDescription != null) {
            propertyNode.put("description", propertyDescription);
        }
        propertyNode.put("type", propertyType);

        if ("object".equals(propertyType)) {
            ObjectNode childPropertiesNode = new ObjectMapper().createObjectNode();
            childPropertiesNode.setAll(toAnthropicParameterProperties((Map<String, Map<String, Object>>) propertyMetadata.get("properties")));
            propertyNode.set("properties", childPropertiesNode);
        }

        if ("array".equals(propertyType)) {
            propertyNode.set("items", toAnthropicParameter((Map<String, Object>) propertyMetadata.get("items")));
        }

        if (propertyMetadata.get("enum") != null) {
            ArrayNode enumValues = new ObjectMapper().createArrayNode();
            ((List<String>) propertyMetadata.get("enum")).forEach(enumValues::add);
            propertyNode.set("enum", enumValues);
        }

        return propertyNode;
    }

    private List<BedrockAnthropicMessage> getAnthropicMessages(List<ChatMessage> messages) {
        List<ChatMessage> noSystemMessages = messages.stream()
                .filter(message -> message.type() != ChatMessageType.SYSTEM)
                .collect(Collectors.toList());

        List<BedrockAnthropicMessage> anthropicMessages = new ArrayList<>();
        List<BedrockAnthropicContent> toolContents = new ArrayList<>();

        for (ChatMessage message : noSystemMessages) {
            List<BedrockAnthropicContent> contents = getAnthropicContent(message);

            if (message instanceof ToolExecutionResultMessage) {
                toolContents.addAll(getAnthropicContent(message));
                continue;
            } else {
                if (!toolContents.isEmpty()) {
                    anthropicMessages.add(new BedrockAnthropicMessage("user", toolContents));
                    toolContents = new ArrayList<>();
                }

                if (message instanceof UserMessage) {
                    anthropicMessages.add(new BedrockAnthropicMessage("user", contents));
                }

                if (message instanceof AiMessage) {
                    anthropicMessages.add(new BedrockAnthropicMessage("assistant", contents));
                }
            }
        }

        if (!toolContents.isEmpty()) {
            anthropicMessages.add(new BedrockAnthropicMessage("user", toolContents));
        }

        return anthropicMessages;
    }

    private List<BedrockAnthropicContent> getAnthropicContent(ChatMessage message) {
        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            List<BedrockAnthropicContent> contents = new ArrayList<>();

            if (isNotNullOrBlank(aiMessage.text())) {
                contents.add(new BedrockAnthropicContent("text", aiMessage.text()));
            }

            if (aiMessage.hasToolExecutionRequests()) {
                List<BedrockAnthropicContent> toolUseRequests = aiMessage.toolExecutionRequests().stream()
                        .map(toolExecutionRequest -> BedrockAnthropicContent.builder()
                                .id(toolExecutionRequest.id())
                                .type("tool_use")
                                .name(toolExecutionRequest.name())
                                .input(Json.fromJson(toolExecutionRequest.arguments(), Map.class))
                                .build())
                        .collect(Collectors.toList());

                contents.addAll(toolUseRequests);
            }

            return contents;
        } else if (message instanceof UserMessage) {
            return ((UserMessage) message).contents().stream()
                .map(BedrockAnthropicMessageChatModel::mapContentToAnthropic)
                .collect(Collectors.toList());
        } else if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) message;
            return Collections.singletonList(
                    BedrockAnthropicContent.builder()
                            .type("tool_result")
                            .tool_use_id(toolExecutionResultMessage.id())
                            .content(toolExecutionResultMessage.text())
                            .build()
            );
        } else {
            throw new IllegalArgumentException("Unknown message type: " + message.type());
        }
    }

    private static BedrockAnthropicContent mapContentToAnthropic(Content content) {
        if (content instanceof TextContent) {
            return new BedrockAnthropicContent("text", ((TextContent) content).text());
        } else if (content instanceof ImageContent) {
            ImageContent imageContent = (ImageContent) content;
            if (imageContent.image().url() != null) {
                throw new IllegalArgumentException("Anthropic does not support images as URLs, only as Base64-encoded strings");
            }
            BedrockAnthropicImageSource imageSource = new BedrockAnthropicImageSource(
                "base64",
                ensureNotBlank(imageContent.image().mimeType(), "mimeType"),
                ensureNotBlank(imageContent.image().base64Data(), "base64Data")
            );
            return new BedrockAnthropicContent("image", imageSource);
        } else {
            throw new IllegalArgumentException("Unknown content type: " + content);
        }
    }

    private String getAnthropicSystemPrompt(List<ChatMessage> messages) {
        return messages.stream()
            .filter(message -> message.type() == ChatMessageType.SYSTEM)
            .map(ChatMessage::text)
            .collect(joining("\n"));
    }

    private String getAnthropicRole(ChatMessage message) {
        return message.type() == ChatMessageType.AI ? "assistant" : "user";
    }

    @Override
    public Class<BedrockAnthropicMessageChatModelResponse> getResponseClassType() {
        return BedrockAnthropicMessageChatModelResponse.class;
    }

    /**
     * Bedrock Anthropic model ids
     */
    @Getter
    public enum Types {
        AnthropicClaudeInstantV1("anthropic.claude-instant-v1"),
        AnthropicClaudeV2("anthropic.claude-v2"),
        AnthropicClaudeV2_1("anthropic.claude-v2:1"),
        AnthropicClaude3SonnetV1("anthropic.claude-3-sonnet-20240229-v1:0"),
        AnthropicClaude3_5SonnetV1("anthropic.claude-3-5-sonnet-20240620-v1:0"),
        AnthropicClaude3HaikuV1("anthropic.claude-3-haiku-20240307-v1:0");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }
    }
}
