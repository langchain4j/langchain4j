package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.stream.Collectors.joining;

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

import java.util.*;
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
        return generate(messages, (ToolSpecification) null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(
                messages,
                Optional.ofNullable(toolSpecification)
                        .map(Collections::singletonList)
                        .orElse(Collections.emptyList())
        );
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
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
        toolParameters.properties().forEach((parameterName, parameterMetadata) -> {
            ObjectNode parameterNode = new ObjectMapper().createObjectNode();
            parameterMetadata.forEach((metadataName, metadataValue) -> parameterNode.put(metadataName, metadataValue.toString()));
            propertiesNode.set(parameterName, parameterNode);
        });

        ArrayNode requiredNode = new ObjectMapper().createArrayNode();
        toolParameters.required().forEach(requiredNode::add);

        ObjectNode inputSchemaNode = new ObjectMapper().createObjectNode();
        inputSchemaNode.put("type", "object");
        inputSchemaNode.set("properties", propertiesNode);
        inputSchemaNode.set("required", requiredNode);

        return inputSchemaNode;
    }

    private List<BedrockAnthropicMessage> getAnthropicMessages(List<ChatMessage> messages) {
        return messages.stream()
            .filter(message -> message.type() != ChatMessageType.SYSTEM)
            .map(message -> new BedrockAnthropicMessage(getAnthropicRole(message), getAnthropicContent(message)))
            .collect(Collectors.toList());
    }

    private List<BedrockAnthropicContent> getAnthropicContent(ChatMessage message) {
        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;

            if (!aiMessage.hasToolExecutionRequests()) {
                return Collections.singletonList(new BedrockAnthropicContent("text", aiMessage.text()));
            }

            List<BedrockAnthropicContent> toolUseRequests = aiMessage.toolExecutionRequests().stream()
                    .map(toolExecutionRequest -> BedrockAnthropicContent.builder()
                            .id(toolExecutionRequest.id())
                            .type("tool_use")
                            .name(toolExecutionRequest.name())
                            .input(Json.fromJson(toolExecutionRequest.arguments(), Map.class))
                            .build())
                    .collect(Collectors.toList());

            return toolUseRequests;
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
