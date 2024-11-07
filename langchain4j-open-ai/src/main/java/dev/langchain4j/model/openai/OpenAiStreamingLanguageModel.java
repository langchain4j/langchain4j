package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.completion.CompletionChoice;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.ai4j.openai4j.shared.StreamOptions;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.language.TokenCountEstimator;
import dev.langchain4j.model.openai.spi.OpenAiStreamingLanguageModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.net.Proxy;
import java.time.Duration;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.DEFAULT_USER_AGENT;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.OPENAI_URL;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO_INSTRUCT;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

/**
 * Represents an OpenAI language model with a completion interface, such as gpt-3.5-turbo-instruct.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
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
                                        String organizationId,
                                        String modelName,
                                        Double temperature,
                                        Duration timeout,
                                        Proxy proxy,
                                        Boolean logRequests,
                                        Boolean logResponses,
                                        Tokenizer tokenizer,
                                        Map<String, String> customHeaders) {

        timeout = getOrDefault(timeout, ofSeconds(60));

        this.client = OpenAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, OPENAI_URL))
                .openAiApiKey(apiKey)
                .organizationId(organizationId)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logStreamingResponses(logResponses)
                .userAgent(DEFAULT_USER_AGENT)
                .customHeaders(customHeaders)
                .build();
        this.modelName = getOrDefault(modelName, GPT_3_5_TURBO_INSTRUCT);
        this.temperature = getOrDefault(temperature, 0.7);
        this.tokenizer = getOrDefault(tokenizer, OpenAiTokenizer::new);
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {

        CompletionRequest request = CompletionRequest.builder()
                .stream(true)
                .streamOptions(StreamOptions.builder()
                        .includeUsage(true)
                        .build())
                .model(modelName)
                .prompt(prompt)
                .temperature(temperature)
                .build();

        OpenAiStreamingResponseBuilder responseBuilder = new OpenAiStreamingResponseBuilder();

        client.completion(request)
                .onPartialResponse(partialResponse -> {
                    responseBuilder.append(partialResponse);
                    for (CompletionChoice choice : partialResponse.choices()) {
                        String token = choice.text();
                        if (isNotNullOrEmpty(token)) {
                            handler.onNext(token);
                        }
                    }
                })
                .onComplete(() -> {
                    Response<AiMessage> response = responseBuilder.build();
                    handler.onComplete(Response.from(
                            response.content().text(),
                            response.tokenUsage(),
                            response.finishReason()
                    ));
                })
                .onError(handler::onError)
                .execute();
    }

    @Override
    public int estimateTokenCount(String prompt) {
        return tokenizer.estimateTokenCountInText(prompt);
    }

    /**
     * @deprecated Please use {@code builder()} instead, and explicitly set the model name and,
     * if necessary, other parameters.
     * <b>The default values for the model name and temperature will be removed in future releases!</b>
     */
    @Deprecated(forRemoval = true)
    public static OpenAiStreamingLanguageModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static OpenAiStreamingLanguageModelBuilder builder() {
        for (OpenAiStreamingLanguageModelBuilderFactory factory : loadFactories(OpenAiStreamingLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiStreamingLanguageModelBuilder();
    }

    public static class OpenAiStreamingLanguageModelBuilder {

        public OpenAiStreamingLanguageModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public OpenAiStreamingLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder modelName(OpenAiLanguageModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }
    }
}
