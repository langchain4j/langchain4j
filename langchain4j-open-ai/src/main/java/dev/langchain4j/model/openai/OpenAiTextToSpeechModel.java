package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.ModelProvider.OPEN_AI;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.audio.TextToSpeechModel;
import dev.langchain4j.model.audio.TextToSpeechRequest;
import dev.langchain4j.model.audio.TextToSpeechResponse;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.audio.texttospeech.OpenAiTextToSpeechRequest;
import dev.langchain4j.model.openai.internal.audio.texttospeech.OpenAiTextToSpeechResponse;
import dev.langchain4j.model.openai.spi.OpenAiTextToSpeechModelBuilderFactory;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * Represents an OpenAI text-to-speech model with a speech generation interface.
 * The supported models are tts-1, tts-1-hd, gpt-4o-mini-tts, and gpt-4o-mini-tts-2025-12-15. <br/>
 * You can find a description of the parameters
 * <a href="https://platform.openai.com/docs/api-reference/audio/createSpeech">here</a>.
 *
 * @since 1.18.0
 */
@Experimental
public class OpenAiTextToSpeechModel implements TextToSpeechModel {

    /**
     * The maximum input length accepted by the OpenAI speech API, in characters.
     */
    private static final int MAX_INPUT_TEXT_LENGTH = 4096;

    private final OpenAiClient client;
    private final int maxRetries;
    private final String modelName;
    private final String voice;

    public OpenAiTextToSpeechModel(Builder builder) {
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
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.voice = getOrDefault(builder.voice, "alloy");
    }

    @Override
    public TextToSpeechResponse synthesize(TextToSpeechRequest audioRequest) {
        if (audioRequest == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (audioRequest.text().length() > MAX_INPUT_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Input text exceeds the maximum length of " + MAX_INPUT_TEXT_LENGTH + " characters");
        }

        OpenAiTextToSpeechRequest openAiRequest = requestBuilder(audioRequest).build();

        OpenAiTextToSpeechResponse openAiResponse =
                withRetryMappingExceptions(() -> client.textToSpeech(openAiRequest).execute(), maxRetries);

        Audio audio = Audio.builder()
                .binaryData(openAiResponse.audio())
                .mimeType(getOrDefault(openAiResponse.contentType(), "audio/mpeg"))
                .build();
        return TextToSpeechResponse.from(audio);
    }

    private OpenAiTextToSpeechRequest.Builder requestBuilder(TextToSpeechRequest request) {
        return OpenAiTextToSpeechRequest.builder()
                .model(modelName)
                .text(request.text())
                .voice(getOrDefault(request.voice(), voice));
    }

    @Override
    public ModelProvider provider() {
        return OPEN_AI;
    }

    public static Builder builder() {
        for (OpenAiTextToSpeechModelBuilderFactory factory : loadFactories(OpenAiTextToSpeechModelBuilderFactory.class)) {
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
        private String voice;

        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        public Builder() {
            // This is public so it can be extended
        }

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
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

        public Builder modelName(OpenAiTextToSpeechModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder voice(String voice) {
            this.voice = voice;
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

        public OpenAiTextToSpeechModel build() {
            return new OpenAiTextToSpeechModel(this);
        }
    }
}
