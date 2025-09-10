package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.model.ModelProvider.WATSONX;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toCollection;

import com.ibm.watsonx.ai.chat.ChatHandler;
import com.ibm.watsonx.ai.chat.ChatResponse.ResultChoice;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatParameters;
import com.ibm.watsonx.ai.chat.model.CompletedToolCall;
import com.ibm.watsonx.ai.chat.model.PartialChatResponse;
import com.ibm.watsonx.ai.chat.model.PartialToolCall;
import com.ibm.watsonx.ai.chat.model.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A {@link StreamingChatModel} implementation that integrates IBM watsonx.ai with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 *
 * StreamingChatModel chatModel = WatsonxStreamingChatModel.builder()
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
public class WatsonxStreamingChatModel extends WatsonxChat implements StreamingChatModel {

    private WatsonxStreamingChatModel(Builder builder) {
        super(builder);
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {

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
        chatService.chatStreaming(
                watsonxChatRequest
                        .messages(messages)
                        .tools(tools)
                        .parameters(parameters)
                        .build(),
                new ChatHandler() {
                    @Override
                    public void onCompleteResponse(com.ibm.watsonx.ai.chat.ChatResponse completeResponse) {

                        ResultChoice choice = completeResponse.getChoices().get(0);
                        FinishReason finishReason = Converter.toFinishReason(choice.getFinishReason());
                        TokenUsage tokenUsage = new TokenUsage(
                                completeResponse.getUsage().getPromptTokens(),
                                completeResponse.getUsage().getCompletionTokens(),
                                completeResponse.getUsage().getTotalTokens());

                        var assistantMessage = completeResponse.toAssistantMessage();
                        var aiMessage = AiMessage.builder();

                        if (isNotNullOrBlank(assistantMessage.refusal()))
                            handler.onError(new ContentFilteredException(assistantMessage.refusal()));

                        if (nonNull(tags)) {
                            aiMessage.thinking(completeResponse.extractThinking());
                            aiMessage.text(completeResponse.extractContent());
                        } else {
                            aiMessage.text(assistantMessage.content());
                        }

                        if (nonNull(assistantMessage.toolCalls())) {
                            aiMessage.toolExecutionRequests(assistantMessage.toolCalls().stream()
                                    .map(Converter::toToolExecutionRequest)
                                    .toList());
                        }

                        ChatResponse chatResponse = ChatResponse.builder()
                                .aiMessage(aiMessage.build())
                                .metadata(WatsonxChatResponseMetadata.builder()
                                        .created(completeResponse.getCreated())
                                        .modelVersion(completeResponse.getModelVersion())
                                        .finishReason(finishReason)
                                        .id(completeResponse.getId())
                                        .modelName(completeResponse.getModelId())
                                        .tokenUsage(tokenUsage)
                                        .build())
                                .build();

                        handler.onCompleteResponse(chatResponse);
                    }

                    @Override
                    public void onError(Throwable error) {
                        handler.onError(WatsonxExceptionMapper.INSTANCE.mapException(error));
                    }

                    @Override
                    public void onPartialResponse(String partialResponse, PartialChatResponse partialChatResponse) {
                        handler.onPartialResponse(partialResponse);
                    }

                    @Override
                    public void onCompleteToolCall(CompletedToolCall completedToolCall) {
                        handler.onCompleteToolCall(Converter.toCompleteToolCall(completedToolCall.toolCall()));
                    }

                    @Override
                    public void onPartialThinking(String partialThinking, PartialChatResponse partialChatResponse) {
                        handler.onPartialThinking(new PartialThinking(partialThinking));
                    }

                    @Override
                    public void onPartialToolCall(PartialToolCall partialToolCall) {
                        handler.onPartialToolCall(Converter.toPartialToolCall(partialToolCall));
                    }
                });
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
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public ModelProvider provider() {
        return WATSONX;
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * StreamingChatModel chatModel = WatsonxStreamingChatModel.builder()
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
     *
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing {@link WatsonxStreamingChatModel} instances with configurable parameters.
     */
    public static class Builder extends WatsonxChat.Builder<Builder> {

        private Builder() {}

        public WatsonxStreamingChatModel build() {
            return new WatsonxStreamingChatModel(this);
        }
    }
}
