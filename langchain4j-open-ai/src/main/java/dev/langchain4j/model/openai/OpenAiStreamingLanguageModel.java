package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.completion.CompletionChoice;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.ai4j.openai4j.shared.StreamOptions;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.language.TokenCountEstimator;
import dev.langchain4j.model.openai.spi.OpenAiStreamingLanguageModelBuilderFactory;
import dev.langchain4j.model.output.Response;

import java.net.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

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
                    ChatResponse chatResponse = responseBuilder.build();
                    handler.onComplete(Response.from(
                            chatResponse.aiMessage().text(),
                            chatResponse.metadata().tokenUsage(),
                            chatResponse.metadata().finishReason()
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

        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String modelName;
        private Double temperature;
        private Duration timeout;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;
        private Tokenizer tokenizer;
        private Map<String, String> customHeaders;

        public OpenAiStreamingLanguageModelBuilder() {
            // This is public so it can be extended
        }

        public OpenAiStreamingLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder modelName(OpenAiLanguageModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OpenAiStreamingLanguageModel build() {
            return new OpenAiStreamingLanguageModel(
                    this.baseUrl,
                    this.apiKey,
                    this.organizationId,
                    this.modelName,
                    this.temperature,
                    this.timeout,
                    this.proxy,
                    this.logRequests,
                    this.logResponses,
                    this.tokenizer,
                    this.customHeaders
            );
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", OpenAiStreamingLanguageModelBuilder.class.getSimpleName() + "[", "]")
                    .add("baseUrl='" + baseUrl + "'")
                    .add("organizationId='" + organizationId + "'")
                    .add("modelName='" + modelName + "'")
                    .add("temperature=" + temperature)
                    .add("timeout=" + timeout)
                    .add("proxy=" + proxy)
                    .add("logRequests=" + logRequests)
                    .add("logResponses=" + logResponses)
                    .add("tokenizer=" + tokenizer)
                    .add("customHeaders=" + customHeaders)
                    .toString();
        }
    }
}
