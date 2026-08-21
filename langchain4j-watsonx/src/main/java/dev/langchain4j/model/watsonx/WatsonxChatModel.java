package dev.langchain4j.model.watsonx;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A {@link ChatModel} implementation that integrates IBM watsonx.ai foundation models with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * ChatModel chatModel = WatsonxChatModel.builder()
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
public class WatsonxChatModel extends WatsonxChat implements ChatModel {

    private WatsonxChatModel(Builder builder) {
        super(builder);
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return executeChat(chatRequest);
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * ChatModel chatModel = WatsonxChatModel.builder()
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
