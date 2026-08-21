package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.OLLAMA;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.aiMessageFrom;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.chatResponseMetadataFrom;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.toOllamaChatRequest;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.spi.OllamaChatModelBuilderFactory;
import java.util.List;
import java.util.Set;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 * <br>
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama API parameters</a>.
 */
public class OllamaChatModel extends OllamaBaseChatModel implements ChatModel {

    private final int maxRetries;

    public OllamaChatModel(OllamaChatModelBuilder builder) {
        init(builder);
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        validate(chatRequest.parameters());

        OllamaChatRequest ollamaChatRequest = toOllamaChatRequest(chatRequest, false);
        OllamaChatResponse ollamaChatResponse =
                withRetryMappingExceptions(() -> client.chat(ollamaChatRequest), maxRetries);

        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(ollamaChatResponse.getMessage(), this.returnThinking))
                .metadata(chatResponseMetadataFrom(ollamaChatResponse))
                .build();
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
    public ModelProvider provider() {
        return OLLAMA;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    public static OllamaChatModelBuilder builder() {
        for (OllamaChatModelBuilderFactory factory : loadFactories(OllamaChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OllamaChatModelBuilder();
    }

    public static class OllamaChatModelBuilder extends Builder<OllamaChatModel, OllamaChatModelBuilder> {

        private Integer maxRetries;

        public OllamaChatModelBuilder() {
            // This is public so it can be extended
        }

        @Override
        protected OllamaChatModelBuilder self() {
            return this;
        }

        public OllamaChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        @Override
        public OllamaChatModel build() {
            return new OllamaChatModel(this);
        }
    }
}
