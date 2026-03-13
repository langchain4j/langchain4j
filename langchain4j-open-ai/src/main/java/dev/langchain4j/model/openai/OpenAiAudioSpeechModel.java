package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.OPEN_AI;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.audio.speech.AudioSpeechModel;
import dev.langchain4j.model.audio.speech.AudioSpeechRequest;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.audio.speech.OpenAiAudioSpeechRequest;
import dev.langchain4j.model.openai.spi.OpenAiAudioSpeechModelBuilderFactory;
import java.time.Duration;
import org.slf4j.Logger;

@Experimental
public class OpenAiAudioSpeechModel implements AudioSpeechModel {

    private final OpenAiClient client;
    private final int maxRetries;
    private final String modelName;

    public OpenAiAudioSpeechModel(Builder builder) {
        this.client = OpenAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_OPENAI_URL))
                .apiKey(builder.apiKey)
                .organizationId(builder.organizationId)
                .projectId(builder.projectId)
                .connectTimeout(getOrDefault(builder.timeout, ofSeconds(15)))
                .readTimeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .userAgent(DEFAULT_USER_AGENT)
                .build();
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.modelName = builder.modelName;
    }

    @Override
    public byte[] generate(AudioSpeechRequest audioRequest) {
        if (audioRequest == null || audioRequest.text() == null) {
            throw new IllegalArgumentException("Request and input text are required");
        }

        OpenAiAudioSpeechRequest openAiRequest = requestBuilder(audioRequest).build();

        return withRetryMappingExceptions(
                () -> client.audioSpeech(openAiRequest).executeInputStream(), maxRetries);
    }

    private OpenAiAudioSpeechRequest.Builder requestBuilder(AudioSpeechRequest request) {
        return OpenAiAudioSpeechRequest.builder()
                .model(modelName)
                .inputText(request.text())
                .voice("alloy");
    }

    @Override
    public ModelProvider provider() {
        return OPEN_AI;
    }

    public static Builder builder() {
        for (OpenAiAudioSpeechModelBuilderFactory factory : loadFactories(OpenAiAudioSpeechModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String projectId;
        private String modelName;

        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        public Builder() {
            // This is public so it can be extended
        }

        public Builder httpClientProvider(HttpClientBuilder httpClientProvider) {
            this.httpClientBuilder = httpClientProvider;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder modelName(OpenAiAudioSpeechModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public OpenAiAudioSpeechModel build() {
            return new OpenAiAudioSpeechModel(this);
        }
    }
}
