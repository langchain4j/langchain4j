package dev.langchain4j.model.oracle.oci.genai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.oracle.bmc.generativeaiinference.model.AssistantMessage;
import com.oracle.bmc.generativeaiinference.model.ChatContent;
import com.oracle.bmc.generativeaiinference.model.FunctionCall;
import com.oracle.bmc.generativeaiinference.model.FunctionDefinition;
import com.oracle.bmc.generativeaiinference.model.GenericChatRequest;
import com.oracle.bmc.generativeaiinference.model.ImageContent;
import com.oracle.bmc.generativeaiinference.model.ImageUrl;
import com.oracle.bmc.generativeaiinference.model.Message;
import com.oracle.bmc.generativeaiinference.model.SystemMessage;
import com.oracle.bmc.generativeaiinference.model.TextContent;
import com.oracle.bmc.generativeaiinference.model.ToolCall;
import com.oracle.bmc.generativeaiinference.model.ToolDefinition;
import com.oracle.bmc.generativeaiinference.model.ToolMessage;
import com.oracle.bmc.generativeaiinference.model.UserMessage;
import com.oracle.bmc.http.client.Serializer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

abstract class BaseGenericChatModel<T extends BaseGenericChatModel<T>> extends BaseChatModel<T> {

    private final Builder<?, ?> builder;

    BaseGenericChatModel(Builder<?, ?> builder) {
        super(builder);
        this.builder = builder;
    }

    /**
     * Maps lc4j chat request and sets configured properties.
     *
     * @param chatRequest lc4j chat request
     * @return OCI BMC generic chat request
     */
    protected GenericChatRequest.Builder prepareRequest(ChatRequest chatRequest) {
        var requestBuilder = map(chatRequest);

        // Common for Generic and Cohere
        setIfNotNull(builder.maxTokens(), requestBuilder::maxTokens);
        setIfNotNull(builder.topK(), requestBuilder::topK);
        setIfNotNull(builder.topP(), requestBuilder::topP);
        setIfNotNull(builder.temperature(), requestBuilder::temperature);
        setIfNotNull(builder.frequencyPenalty(), requestBuilder::frequencyPenalty);
        setIfNotNull(builder.presencePenalty(), requestBuilder::presencePenalty);
        setIfNotNull(builder.seed(), requestBuilder::seed);
        setIfNotNull(builder.stop(), requestBuilder::stop);

        // Generic specific
        setIfNotNull(builder.numGenerations(), requestBuilder::numGenerations);
        setIfNotNull(builder.logProbs(), requestBuilder::logProbs);
        setIfNotNull(builder.logitBias(), requestBuilder::logitBias);

        // Per-request overrides
        var params = chatRequest.parameters();
        setIfNotNull(params.maxOutputTokens(), requestBuilder::maxTokens);
        setIfNotNull(params.topK(), requestBuilder::topK);
        setIfNotNull(params.topP(), requestBuilder::topP);
        setIfNotNull(params.temperature(), requestBuilder::temperature);
        setIfNotNull(params.frequencyPenalty(), requestBuilder::frequencyPenalty);
        setIfNotNull(params.presencePenalty(), requestBuilder::presencePenalty);
        setIfNotNull(params.stopSequences(), requestBuilder::stop);

        return requestBuilder;
    }

    static GenericChatRequest.Builder map(ChatRequest chatRequest) {
        List<Message> messages =
                chatRequest.messages().stream().map(BaseGenericChatModel::map).toList();

        var bmcTools = Optional.ofNullable(chatRequest.toolSpecifications()).orElse(List.of()).stream()
                .map(toolSpec -> {
                    var b = FunctionDefinition.builder();

                    if (toolSpec.parameters() != null) {
                        b.parameters(map(toolSpec));
                    }

                    return b.name(toolSpec.name())
                            .description(toolSpec.description())
                            .build();
                })
                .map(ToolDefinition.class::cast)
                .toList();

        var builder = GenericChatRequest.builder().messages(messages);

        var parameters = chatRequest.parameters();
        setIfNotNull(parameters.frequencyPenalty(), builder::frequencyPenalty);
        setIfNotNull(parameters.maxOutputTokens(), builder::maxTokens);
        setIfNotNull(parameters.topK(), builder::topK);
        setIfNotNull(parameters.presencePenalty(), builder::presencePenalty);
        setIfNotNull(parameters.temperature(), builder::temperature);

        if (!bmcTools.isEmpty()) {
            builder.tools(bmcTools);
        }

        return builder;
    }

    static Message map(ChatMessage chatMessage) {
        return switch (chatMessage.type()) {
            case USER -> {
                var userMessage = (dev.langchain4j.data.message.UserMessage) chatMessage;
                yield UserMessage.builder()
                        .content(userMessage.contents().stream()
                                .map(BaseGenericChatModel::map)
                                .toList())
                        .build();
            }
            case SYSTEM -> {
                var systemMessage = (dev.langchain4j.data.message.SystemMessage) chatMessage;
                yield SystemMessage.builder()
                        .content(List.of(
                                TextContent.builder().text(systemMessage.text()).build()))
                        .build();
            }
            case AI -> {
                var aiMessage = (dev.langchain4j.data.message.AiMessage) chatMessage;

                var assistantMessageBuilder = AssistantMessage.builder();

                if (aiMessage.hasToolExecutionRequests()) {
                    var toolCalls = new ArrayList<ToolCall>();
                    for (ToolExecutionRequest toolExecReq : aiMessage.toolExecutionRequests()) {
                        toolCalls.add(FunctionCall.builder()
                                .name(toolExecReq.name())
                                .id(toolExecReq.id())
                                .arguments(toolExecReq.arguments())
                                .build());
                    }
                    assistantMessageBuilder.toolCalls(toolCalls);
                }

                yield assistantMessageBuilder
                        .content(List.of(
                                TextContent.builder().text(aiMessage.text()).build()))
                        .build();
            }
            case TOOL_EXECUTION_RESULT -> {
                var toolMessage = (dev.langchain4j.data.message.ToolExecutionResultMessage) chatMessage;
                yield ToolMessage.builder()
                        .content(List.of(
                                TextContent.builder().text(toolMessage.text()).build()))
                        .toolCallId(toolMessage.id())
                        .build();
            }
            default -> throw new IllegalStateException("Unsupported chat message: " + chatMessage.type());
        };
    }

    static ChatContent map(Content lc4jContent) {
        return switch (lc4jContent.type()) {
            case TEXT -> {
                var textContent = (dev.langchain4j.data.message.TextContent) lc4jContent;
                yield TextContent.builder().text(textContent.text()).build();
            }
            case IMAGE -> {
                var imageContent = (dev.langchain4j.data.message.ImageContent) lc4jContent;
                Image image = imageContent.image();
                var ociImageBuilder = ImageUrl.builder();
                if (image.url() != null) {
                    ociImageBuilder.url(image.url().toString());
                } else {
                    // rfc2397
                    ociImageBuilder.url("data:" + image.mimeType() + ";base64," + image.base64Data());
                }

                yield ImageContent.builder()
                        .imageUrl(ociImageBuilder
                                .detail(ImageUrl.Detail.create(
                                        imageContent.detailLevel().name()))
                                .build())
                        .build();
            }
            default ->
                throw new IllegalStateException("Unsupported content type: "
                        + lc4jContent.type()
                        + " only TEXT and IMAGE content types are supported.");
        };
    }

    static ToolFunctionParameters map(ToolSpecification toolSpecification) {

        final JsonObjectSchema lc4jParams = toolSpecification.parameters();

        ToolFunctionParameters result = new ToolFunctionParameters();

        for (var entry : lc4jParams.properties().entrySet()) {
            Map<String, Object> map = JsonSchemaElementUtils.toMap(entry.getValue());
            result.setProperties(Map.of(entry.getKey(), map));
            result.required.add(entry.getKey());
        }

        return result;
    }

    /**
     * <pre>{@code
     * {
     *     "type": "function",
     *     "function": {
     *         "name": "currentTime",
     *         "description": "Returns current local time now at provided location.",
     *         "parameters": {
     *             "type": "object",
     *             "properties": {
     *                 "location": {
     *                     "type": "string",
     *                     "description": "The location where the time will be determined."
     *                 }
     *             },
     *             "required": [
     *                 "location"
     *             ]
     *         }
     *     }
     * }
     * }</pre>
     */
    @JsonPropertyOrder({"type", "properties", "required"})
    static class ToolFunctionParameters {

        @JsonProperty("type")
        private String type = "object";

        @JsonProperty("properties")
        private Map<String, Object> properties = new HashMap<>();

        @JsonProperty("required")
        private List<String> required = new ArrayList<>();

        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }

        @Override
        public String toString() {
            try {
                return Serializer.getDefault().writeValueAsString(this);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    abstract static class Builder<T extends BaseGenericChatModel<T>, B extends Builder<T, B>>
            extends BaseChatModel.Builder<T, B> {

        private Integer numGenerations;
        private Integer logProbs;
        private Object logitBias;

        protected Builder() {}

        /**
         * The number of generated texts that will be returned.
         *
         * @param numGenerations Value to set
         * @return builder
         */
        public B numGenerations(Integer numGenerations) {
            this.numGenerations = numGenerations;
            return self();
        }

        Integer numGenerations() {
            return numGenerations;
        }

        /**
         * Includes the logarithmic probabilities for the most likely output tokens and the chosen tokens.
         * For example, if the log probability is 5, the API returns a list of the 5 most likely tokens.
         * The API returns the log probability of the sampled token,
         * so there might be up to logprobs+1 elements in the response.
         *
         * @param logProbs Value to set
         * @return builder
         */
        public B logProbs(Integer logProbs) {
            this.logProbs = logProbs;
            return self();
        }

        Integer logProbs() {
            return logProbs;
        }

        /**
         * Modifies the likelihood of specified tokens that appear in the completion.
         * Example: '{"6395": 2, "8134": 1, "21943": 0.5, "5923": -100}'
         *
         * @param logitBias Value to set
         * @return builder
         */
        public B logitBias(Object logitBias) {
            this.logitBias = logitBias;
            return self();
        }

        Object logitBias() {
            return logitBias;
        }

        /**
         * Build new instance.
         *
         * @return the instance
         */
        public abstract T build();
    }
}
