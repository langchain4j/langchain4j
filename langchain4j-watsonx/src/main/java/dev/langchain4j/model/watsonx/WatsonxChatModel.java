package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.model.ModelProvider.WATSONX;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toCollection;

import com.ibm.watsonx.ai.chat.ChatResponse.ResultChoice;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatParameters;
import com.ibm.watsonx.ai.chat.model.ChatUsage;
import com.ibm.watsonx.ai.chat.model.ResultMessage;
import com.ibm.watsonx.ai.chat.model.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A {@link ChatModel} implementation that integrates IBM watsonx.ai with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * ChatModel chatModel = WatsonxChatModel.builder()
 *     .url("https://...") // or use CloudRegion
 *     .apiKey("...")
 *     .projectId("...")
 *     .modelName("ibm/granite-3-8b-instruct")
 *     .maxOutputTokens(0)
 *     .temperature(0.7)
 *     .build();
 * }</pre>
 *
 */
public class WatsonxChatModel extends WatsonxChat implements ChatModel {

    private WatsonxChatModel(Builder builder) {
        super(builder);
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {

        validate(chatRequest.parameters());

        List<ToolSpecification> toolSpecifications = getOrDefault(
                chatRequest.parameters().toolSpecifications(), defaultRequestParameters.toolSpecifications());

        List<ChatMessage> messages =
                chatRequest.messages().stream().map(Converter::toChatMessage).collect(toCollection(ArrayList::new));

        List<Tool> tools = nonNull(toolSpecifications) && toolSpecifications.size() > 0
                ? toolSpecifications.stream().map(Converter::toTool).toList()
                : null;

        var watsonxChatRequest = com.ibm.watsonx.ai.chat.ChatRequest.builder();

        if (isThinkingActivable(chatRequest.messages(), toolSpecifications)) {
            messages.add(THINKING);
            watsonxChatRequest.thinking(tags);
        }

        ChatParameters parameters = Converter.toChatParameters(chatRequest.parameters());

        com.ibm.watsonx.ai.chat.ChatResponse chatResponse =
                WatsonxExceptionMapper.INSTANCE.withExceptionMapper(() -> chatService.chat(watsonxChatRequest
                        .messages(messages)
                        .tools(tools)
                        .parameters(parameters)
                        .build()));

        ResultChoice choice = chatResponse.getChoices().get(0);
        ChatUsage usage = chatResponse.getUsage();
        ResultMessage message = choice.getMessage();

        if (isNotNullOrBlank(message.refusal())) throw new ContentFilteredException(message.refusal());

        AiMessage.Builder aiMessage = AiMessage.builder();

        if (nonNull(message.toolCalls()) && !message.toolCalls().isEmpty()) {
            aiMessage.toolExecutionRequests(message.toolCalls().stream()
                    .map(Converter::toToolExecutionRequest)
                    .toList());
        } else if (nonNull(tags)) {
            aiMessage.thinking(chatResponse.extractThinking());
            aiMessage.text(chatResponse.extractContent());
        } else {
            aiMessage.text(message.content());
        }

        FinishReason finishReason = Converter.toFinishReason(choice.getFinishReason());
        TokenUsage tokenUsage =
                new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());

        return ChatResponse.builder()
                .aiMessage(aiMessage.build())
                .metadata(WatsonxChatResponseMetadata.builder()
                        .created(chatResponse.getCreated())
                        .modelVersion(chatResponse.getModelVersion())
                        .finishReason(finishReason)
                        .id(chatResponse.getId())
                        .modelName(chatResponse.getModelId())
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
        return defaultRequestParameters;
    }

    @Override
    public ModelProvider provider() {
        return WATSONX;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * ChatModel chatModel = WatsonxChatModel.builder()
     *     .url("https://...") // or use CloudRegion
     *     .apiKey("...")
     *     .projectId("...")
     *     .modelName("ibm/granite-3-8b-instruct")
     *     .maxOutputTokens(0)
     *     .temperature(0.7)
     *     .build();
     * }</pre>
     *
     * @return {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing {@link WatsonxChatModel} instances with configurable parameters.
     */
    public static class Builder extends WatsonxChat.Builder<Builder> {

        private Builder() {}

        public WatsonxChatModel build() {
            return new WatsonxChatModel(this);
        }
    }
}
