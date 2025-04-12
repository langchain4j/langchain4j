package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.OLLAMA;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.aiMessageFrom;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.chatResponseMetadataFrom;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.toOllamaChatRequest;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.validate;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.spi.OllamaChatModelBuilderFactory;
import java.util.List;
import java.util.Set;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 * <br>
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama API parameters</a>.
 */
public class OllamaChatModel extends OllamaBaseChatModel implements ChatLanguageModel {

    private final Integer maxRetries;

    public OllamaChatModel(OllamaChatModelBuilder builder) {

        init(builder);
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {

        OllamaChatRequestParameters parameters = (OllamaChatRequestParameters) chatRequest.parameters();
        validate(parameters);

        OllamaChatRequest ollamaChatRequest = toOllamaChatRequest(chatRequest, false);
        OllamaChatResponse ollamaChatResponse =
                withRetryMappingExceptions(() -> client.chat(ollamaChatRequest), maxRetries);

        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(ollamaChatResponse))
                .metadata(chatResponseMetadataFrom(ollamaChatResponse))
                .build();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
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
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
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
            // By default with Lombok it becomes package private
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
