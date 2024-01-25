package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.spi.OllamaChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.spi.ServiceHelper;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.ollama.OllamaMessagesUtils.toOllamaMessages;
import static java.time.Duration.ofSeconds;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 * <br>
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama API parameters</a>.
 */
public class OllamaChatModel implements ChatLanguageModel {

    private final OllamaClient client;
    private final String modelName;
    private final Options options;
    private final String format;
    private final Integer maxRetries;

    @Builder
    public OllamaChatModel(String baseUrl,
                           String modelName,
                           Double temperature,
                           Integer topK,
                           Double topP,
                           Double repeatPenalty,
                           Integer seed,
                           Integer numPredict,
                           List<String> stop,
                           String format,
                           Duration timeout,
                           Integer maxRetries) {
        this.client = OllamaClient.builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.options = Options.builder()
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .repeatPenalty(repeatPenalty)
                .seed(seed)
                .numPredict(numPredict)
                .stop(stop)
                .build();
        this.format = format;
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ensureNotEmpty(messages, "messages");

        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .messages(toOllamaMessages(messages))
                .options(options)
                .format(format)
                .stream(false)
                .build();

        ChatResponse response = withRetry(() -> client.chat(request), maxRetries);

        return Response.from(
                AiMessage.from(response.getMessage().getContent()),
                new TokenUsage(response.getPromptEvalCount(), response.getEvalCount())
        );
    }

    public static OllamaChatModelBuilder builder() {
        return ServiceHelper.loadFactoryService(
                OllamaChatModelBuilderFactory.class,
                OllamaChatModelBuilder::new
        );
    }

    public static class OllamaChatModelBuilder {
        public OllamaChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }

}
