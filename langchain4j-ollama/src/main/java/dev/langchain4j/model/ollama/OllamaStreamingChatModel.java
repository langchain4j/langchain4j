package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.OLLAMA;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.ollama.spi.OllamaStreamingChatModelBuilderFactory;
import java.util.List;
import java.util.Set;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 * <br>
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama API parameters</a>.
 */
public class OllamaStreamingChatModel extends OllamaBaseChatModel implements StreamingChatModel {

    private final int maxRetries;

    public OllamaStreamingChatModel(OllamaStreamingChatModelBuilder builder) {
        init(builder);
        this.maxRetries = getOrDefault(builder.maxRetries, 0);
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        validate(chatRequest.parameters());
        client.streamingChat(chatRequest, this.returnThinking, handler);
    }

    @Override
    public OllamaChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public int maxRetries() {
        return maxRetries;
    }

    @Override
    public ModelProvider provider() {
        return OLLAMA;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    public static OllamaStreamingChatModelBuilder builder() {
        for (OllamaStreamingChatModelBuilderFactory factory :
                loadFactories(OllamaStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OllamaStreamingChatModelBuilder();
    }

    public static class OllamaStreamingChatModelBuilder
            extends Builder<OllamaStreamingChatModel, OllamaStreamingChatModelBuilder> {

        private Integer maxRetries;

        public OllamaStreamingChatModelBuilder() {
            // This is public so it can be extended
        }

        @Override
        protected OllamaStreamingChatModelBuilder self() {
            return this;
        }

        /**
         * Sets the maximum number of times a failed request is retried, as long as nothing has been streamed
         * to the handler yet. Default: {@code 0} (no retries), unlike the non-streaming model, which retries twice.
         *
         * @param maxRetries the maximum number of retries
         * @return {@code this}
         * @see StreamingChatModel#maxRetries()
         */
        public OllamaStreamingChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        @Override
        public OllamaStreamingChatModel build() {
            return new OllamaStreamingChatModel(this);
        }
    }
}
