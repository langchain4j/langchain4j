package dev.langchain4j.model.watsonx;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * A {@link StreamingChatModel} implementation that integrates IBM watsonx.ai foundation models with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 *
 * StreamingChatModel chatModel = WatsonxStreamingChatModel.builder()
 *     .baseUrl("https://...") // or use CloudRegion
 *     .apiKey("...")
 *     .projectId("...")
 *     .modelName("ibm/granite-4-h-small")
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
        executeChatStreaming(chatRequest, handler);
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * StreamingChatModel chatModel = WatsonxStreamingChatModel.builder()
     *     .baseUrl("https://...") // or use CloudRegion
     *     .apiKey("...")
     *     .projectId("...")
     *     .modelName("ibm/granite-4-h-small")
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
