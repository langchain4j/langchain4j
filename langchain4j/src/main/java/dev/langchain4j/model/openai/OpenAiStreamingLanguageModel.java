package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.language.TokenCountEstimator;
import lombok.Builder;

import java.net.Proxy;
import java.time.Duration;

import static dev.langchain4j.model.openai.InternalOpenAiHelper.OPENAI_URL;
import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_DAVINCI_003;
import static java.time.Duration.ofSeconds;

/**
 * Represents a connection to the OpenAI LLM with a completion interface, such as text-davinci-003.
 * The LLM's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * However, it's recommended to use {@link OpenAiStreamingChatModel} instead,
 * as it offers more advanced features like function calling, multi-turn conversations, etc.
 */
public class OpenAiStreamingLanguageModel implements StreamingLanguageModel, TokenCountEstimator {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Tokenizer tokenizer;

    @Builder
    public OpenAiStreamingLanguageModel(String baseUrl,
                                        String apiKey,
                                        String modelName,
                                        Double temperature,
                                        Duration timeout,
                                        Proxy proxy,
                                        Boolean logRequests,
                                        Boolean logResponses) {

        baseUrl = baseUrl == null ? OPENAI_URL : baseUrl;
        modelName = modelName == null ? TEXT_DAVINCI_003 : modelName;
        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(60) : timeout;

        this.client = OpenAiClient.builder()
                .baseUrl(baseUrl)
                .openAiApiKey(apiKey)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logStreamingResponses(logResponses)
                .build();
        this.modelName = modelName;
        this.temperature = temperature;
        this.tokenizer = new OpenAiTokenizer(this.modelName);
    }

    @Override
    public void process(String text, StreamingResponseHandler handler) {

        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(text)
                .temperature(temperature)
                .build();

        client.completion(request)
                .onPartialResponse(partialResponse -> {
                    String partialResponseText = partialResponse.text();
                    if (partialResponseText != null) {
                        handler.onNext(partialResponseText);
                    }
                })
                .onComplete(handler::onComplete)
                .onError(handler::onError)
                .execute();
    }

    @Override
    public int estimateTokenCount(String prompt) {
        return tokenizer.estimateTokenCountInText(prompt);
    }

    public static OpenAiStreamingLanguageModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
