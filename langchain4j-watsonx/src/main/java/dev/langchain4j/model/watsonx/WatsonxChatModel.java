package dev.langchain4j.model.watsonx;

import static dev.langchain4j.model.ModelProvider.WATSONX;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.chat.ChatResponse.ResultChoice;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatParameters;
import com.ibm.watsonx.ai.chat.model.ChatUsage;
import com.ibm.watsonx.ai.chat.model.ResultMessage;
import com.ibm.watsonx.ai.chat.model.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.watsonx.util.Converter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link ChatModel} implementation that integrates IBM watsonx.ai with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * ChatService chatService = ChatService.builder()
 *     .url("https://...") // or use CloudRegion
 *     .authenticationProvider(authProvider)
 *     .projectId("my-project-id")
 *     .modelId("ibm/granite-3-8b-instruct")
 *     .build();
 *
 * WatsonxChatRequestParameters defaultRequestParameters =
 *     WatsonxChatRequestParameters.builder()
 *         .maxOutputTokens(0)
 *         .temperature(0.7)
 *         .build();
 *
 * ChatModel chatModel = WatsonxChatModel.builder()
 *     .service(chatService)
 *     .defaultRequestParameters(defaultRequestParameters)
 *     .build();
 * }</pre>
 *
 *
 * @see ChatService
 * @see WatsonxChatRequestParameters
 */
public class WatsonxChatModel extends WatsonxChat implements ChatModel {

    protected WatsonxChatModel(Builder builder) {
        super(builder);
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {

        List<ToolSpecification> toolSpecifications = chatRequest.parameters().toolSpecifications();

        List<ChatMessage> messages =
                chatRequest.messages().stream().map(Converter::toChatMessage).toList();

        List<Tool> tools = nonNull(toolSpecifications) && toolSpecifications.size() > 0
                ? toolSpecifications.stream().map(Converter::toTool).toList()
                : null;

        ChatParameters parameters = Converter.toChatParameters(chatRequest);

        com.ibm.watsonx.ai.chat.ChatResponse chatResponse =
                chatProvider.chat(com.ibm.watsonx.ai.chat.ChatRequest.builder()
                        .messages(messages)
                        .tools(tools)
                        .parameters(parameters)
                        .build());

        ResultChoice choice = chatResponse.getChoices().get(0);
        ChatUsage usage = chatResponse.getUsage();
        ResultMessage message = choice.message();

        AiMessage.Builder aiMessage = AiMessage.builder();

        if (nonNull(message.toolCalls()) && !message.toolCalls().isEmpty()) {
            aiMessage.toolExecutionRequests(message.toolCalls().stream()
                    .map(Converter::toToolExecutionRequest)
                    .toList());
        } else if (nonNull(tags)) {
            var parts = chatResponse.toTextByTags(Set.of(tags.think(), tags.response()));
            aiMessage.thinking(parts.get(tags.think()));
            aiMessage.text(parts.get(tags.response()));
        } else {
            aiMessage.text(message.content());
        }

        FinishReason finishReason = Converter.toFinishReason(choice.finishReason());
        TokenUsage tokenUsage =
                new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());

        return ChatResponse.builder()
                .aiMessage(aiMessage.build())
                .metadata(WatsonxChatResponseMetadata.builder()
                        .created(chatResponse.getCreated())
                        .createdAt(chatResponse.getCreatedAt())
                        .finishReason(finishReason)
                        .id(chatResponse.getId())
                        .modelName(chatResponse.getModelId())
                        .model(chatResponse.getModel())
                        .modelVersion(chatResponse.getModelVersion())
                        .object(chatResponse.getObject())
                        .tokenUsage(tokenUsage)
                        .build())
                .build();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return this.defaultRequestParameters;
    }

    @Override
    public ModelProvider provider() {
        return WATSONX;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        var capatibilities = new HashSet<Capability>();
        if (enableJsonSchema
                || (nonNull(defaultRequestParameters.responseFormat())
                        && defaultRequestParameters.responseFormat().type().equals(ResponseFormatType.JSON)
                        && nonNull(defaultRequestParameters.responseFormat().jsonSchema())))
            capatibilities.add(Capability.RESPONSE_FORMAT_JSON_SCHEMA);

        return capatibilities;
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * ChatService chatService = ChatService.builder()
     *     .url("https://...") // or use CloudRegion
     *     .authenticationProvider(authProvider)
     *     .projectId("my-project-id")
     *     .modelId("ibm/granite-3-8b-instruct")
     *     .build();
     *
     * WatsonxChatRequestParameters defaultRequestParameters =
     *     WatsonxChatRequestParameters.builder()
     *         .maxOutputTokens(0)
     *         .temperature(0.7)
     *         .build();
     *
     * ChatModel chatModel = WatsonxChatModel.builder()
     *     .service(chatService)
     *     .defaultRequestParameters(defaultRequestParameters)
     *     .build();
     * }</pre>
     *
     *
     * @see ChatService
     * @see WatsonxChatRequestParameters
     * @return {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing {@link WatsonxChatModel} instances with configurable parameters.
     */
    public static class Builder extends WatsonxChat.Builder<Builder> {

        public WatsonxChatModel build() {
            return new WatsonxChatModel(this);
        }
    }
}
