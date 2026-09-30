package dev.langchain4j.model.watsonx;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * A {@link StreamingChatModel} implementation that integrates the IBM watsonx.ai Model Gateway with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 *
 * StreamingChatModel chatModel = WatsonxGatewayStreamingChatModel.builder()
 *     .baseUrl("https://...") // or use CloudRegion
 *     .apiKey("...")
 *     .modelName("gpt-4o")
 *     .maxOutputTokens(0)
 *     .temperature(0.7)
 *     .build();
 * }</pre>
 *
 */
public class WatsonxGatewayStreamingChatModel extends WatsonxGatewayChat implements StreamingChatModel {

    private WatsonxGatewayStreamingChatModel(Builder builder) {
        super(builder);
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        executeChatStreaming(chatRequest, handler);
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * StreamingChatModel chatModel = WatsonxGatewayStreamingChatModel.builder()
     *     .baseUrl("https://...") // or use CloudRegion
     *     .apiKey("...")
     *     .modelName("gpt-4o")
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
     * Builder class for constructing {@link WatsonxGatewayStreamingChatModel} instances with configurable parameters.
     */
    public static class Builder extends WatsonxGatewayChat.Builder<Builder> {

        private Builder() {}

        public WatsonxGatewayStreamingChatModel build() {
            return new WatsonxGatewayStreamingChatModel(this);
        }
    }
}
