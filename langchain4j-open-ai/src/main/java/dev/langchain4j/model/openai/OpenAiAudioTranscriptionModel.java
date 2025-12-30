package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.ParsedAndRawResponse;
import dev.langchain4j.model.openai.internal.audio.transcription.OpenAiAudioTranscriptionRequest;
import dev.langchain4j.model.openai.internal.audio.transcription.OpenAiAudioTranscriptionResponse;
import dev.langchain4j.model.openai.internal.audio.transcription.AudioFile;
import dev.langchain4j.model.openai.spi.OpenAiAudioTranscriptionModelBuilderFactory;
import org.slf4j.Logger;

import java.time.Duration;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.OPEN_AI;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

/**
 * Represents an OpenAI audio model with a transcription interface, only gpt-4o-transcribe,
 * gpt-4o-mini-transcribe, whisper-1 (which is powered by our open source Whisper V2 model),
 * and gpt-4o-transcribe-diarize are supported. <br/>
 * You can find description of parameters
 * <a href="https://platform.openai.com/docs/api-reference/audio/createTranscription">here</a>.
 *
 * @since 1.10.0
 */
@Experimental
public class OpenAiAudioTranscriptionModel implements AudioTranscriptionModel {

    private final OpenAiClient client;
    private final int maxRetries;
    private final String modelName;

    public OpenAiAudioTranscriptionModel(Builder builder) {
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
    public AudioTranscriptionResponse transcribe(AudioTranscriptionRequest audioRequest) {
        if (audioRequest == null || audioRequest.audio() == null) {
            throw new IllegalArgumentException("Request and audio are required");
        }

        OpenAiAudioTranscriptionRequest openAiRequest = requestBuilder(audioRequest).build();

        ParsedAndRawResponse<OpenAiAudioTranscriptionResponse> parsedAndRawResponse = withRetryMappingExceptions(
                () -> client.audioTranscription(openAiRequest).executeRaw(), maxRetries);

        OpenAiAudioTranscriptionResponse openAiResponse = parsedAndRawResponse.parsedResponse();

        return AudioTranscriptionResponse.from(openAiResponse.text());
    }

    private OpenAiAudioTranscriptionRequest.Builder requestBuilder(AudioTranscriptionRequest request) {
        return OpenAiAudioTranscriptionRequest.builder()
                .model(modelName)
                .file(AudioFile.from(request.audio()))
                .language(request.language())
                .prompt(request.prompt())
                .temperature(request.temperature());
    }

    @Override
    public ModelProvider provider() {
        return OPEN_AI;
    }

    public static Builder builder() {
        for (OpenAiAudioTranscriptionModelBuilderFactory factory : loadFactories(OpenAiAudioTranscriptionModelBuilderFactory.class)) {
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

        public Builder modelName(OpenAiAudioTranscriptionModelName modelName) {
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

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public OpenAiAudioTranscriptionModel build() {
            return new OpenAiAudioTranscriptionModel(this);
        }
    }
}
