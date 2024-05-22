package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.spi.OllamaChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.ollama.OllamaMessagesUtils.toOllamaMessages;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 * <br>
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama API parameters</a>.
 */
public class OllamaChatModel implements ChatLanguageModel {

    private final Logger logger = LoggerFactory.getLogger(OllamaChatModel.class);

    private final OllamaClient client;
    private final String modelName;
    private final Options options;
    private final String format;
    private final Integer maxRetries;
    private final String keepAlive;
    Boolean modelLoadedInMemory = false;

    @Builder
    public OllamaChatModel(String baseUrl,
                           String modelName,
                           Double temperature,
                           Integer topK,
                           Double topP,
                           Double repeatPenalty,
                           Integer seed,
                           Integer numPredict,
                           Integer numCtx,
                           List<String> stop,
                           String format,
                           Duration timeout,
                           Integer maxRetries,
                           String keepAlive,
                           Boolean preload,
                           Map<String, String> customHeaders) {
        this.client = OllamaClient.builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .customHeaders(customHeaders)
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.options = Options.builder()
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .repeatPenalty(repeatPenalty)
                .seed(seed)
                .numPredict(numPredict)
                .numCtx(numCtx)
                .stop(stop)
                .build();
        this.format = format;
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.keepAlive = keepAlive;

        /**
         * Preload the model before the first request is made by sending an empty request.
         * 
         * Extracted from Ollama FAQ documentation:
         * https://github.com/ollama/ollama/blob/main/docs/faq.md#how-can-i-pre-load-a-model-to-get-faster-response-times
         */
        if (preload != null && preload) {
            this.preload();
        }
    }

    public void preload() {
        long startTime = System.currentTimeMillis();
        // Empty prompt is enough to preload the model but langchain4j rejects null or blank prompts
        generate(Arrays.asList(UserMessage.from("Say 'Model loaded'")));
        this.modelLoadedInMemory = true;
        long endTime = System.currentTimeMillis();
        logger.info("Model '{}' preloaded in {} ms", modelName, (endTime - startTime));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ensureNotEmpty(messages, "messages");

        ChatRequest.ChatRequestBuilder requestBuilder = ChatRequest.builder()
                .model(modelName)
                .messages(toOllamaMessages(messages))
                .options(options)
                .format(format)
                .stream(false);

        Optional.ofNullable(keepAlive).ifPresent(requestBuilder::keepAlive);

        ChatRequest request = requestBuilder.build();

        ChatResponse response = withRetry(() -> client.chat(request), maxRetries);
        this.modelLoadedInMemory = true;

        return Response.from(
                AiMessage.from(response.getMessage().getContent()),
                new TokenUsage(response.getPromptEvalCount(), response.getEvalCount())
        );
    }

    public static OllamaChatModelBuilder builder() {
        for (OllamaChatModelBuilderFactory factory : loadFactories(OllamaChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OllamaChatModelBuilder();
    }

    public static class OllamaChatModelBuilder {
        public OllamaChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }

}
