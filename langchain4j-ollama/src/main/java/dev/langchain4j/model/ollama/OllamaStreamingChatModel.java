package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.ollama.spi.OllamaStreamingChatModelBuilderFactory;
import lombok.Builder;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class OllamaStreamingChatModel implements StreamingChatLanguageModel {

    private final Logger logger = LoggerFactory.getLogger(OllamaStreamingChatModel.class);

    private final OllamaClient client;
    private final String modelName;
    private final Options options;
    private final String format;
    private final String keepAlive;
    Boolean modelLoadedInMemory = false;

    @Builder
    public OllamaStreamingChatModel(String baseUrl,
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
        generate(Arrays.asList(UserMessage.from("Say 'Model loaded'")), new NoOpStreamingResponseHandler<AiMessage>());
        this.modelLoadedInMemory = true;
        long endTime = System.currentTimeMillis();
        logger.info("Model '{}' preloaded in {} ms", modelName, (endTime - startTime));
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(messages, "messages");

        ChatRequest.ChatRequestBuilder requestBuilder = ChatRequest.builder()
                .model(modelName)
                .messages(toOllamaMessages(messages))
                .options(options)
                .format(format)
                .stream(true);

        Optional.ofNullable(keepAlive).ifPresent(requestBuilder::keepAlive);

        ChatRequest request = requestBuilder.build();
        
        client.streamingChat(request, handler);
        this.modelLoadedInMemory = true;
    }

    public static OllamaStreamingChatModelBuilder builder() {
        for (OllamaStreamingChatModelBuilderFactory factory : loadFactories(OllamaStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OllamaStreamingChatModelBuilder();
    }

    public static class OllamaStreamingChatModelBuilder {
        public OllamaStreamingChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
