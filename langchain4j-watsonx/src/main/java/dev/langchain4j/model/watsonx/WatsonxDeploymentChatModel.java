package dev.langchain4j.model.watsonx;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A {@link ChatModel} implementation that integrates the IBM watsonx.ai Deployment with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * ChatModel chatModel = WatsonxDeploymentChatModel.builder()
 *     .baseUrl("https://...") // or use CloudRegion
 *     .apiKey("...")
 *     .deploymentId("...")
 *     .maxOutputTokens(0)
 *     .temperature(0.7)
 *     .build();
 * }</pre>
 *
 */
public class WatsonxDeploymentChatModel extends WatsonxDeploymentChat implements ChatModel {

    private WatsonxDeploymentChatModel(Builder builder) {
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
     * ChatModel chatModel = WatsonxDeploymentChatModel.builder()
     *     .baseUrl("https://...") // or use CloudRegion
     *     .apiKey("...")
     *     .deploymentId("...")
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
     * Builder class for constructing {@link WatsonxDeploymentChatModel} instances with configurable parameters.
     */
    public static class Builder extends WatsonxDeploymentChat.Builder<Builder> {

        private Builder() {}

        public WatsonxDeploymentChatModel build() {
            return new WatsonxDeploymentChatModel(this);
        }
    }
}
