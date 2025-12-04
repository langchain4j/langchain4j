package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.OPEN_AI;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.validate;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.ParsedAndRawResponse;
import dev.langchain4j.model.openai.internal.audio.OpenAiAudioTranscriptionRequest;
import dev.langchain4j.model.openai.internal.audio.OpenAiAudioTranscriptionResponse;
import dev.langchain4j.model.openai.spi.OpenAiAudioModelBuilderFactory;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * Represents an OpenAI audio model with a transcription interface, only gpt-4o-transcribe,
 * gpt-4o-mini-transcribe, whisper-1 (which is powered by our open source Whisper V2 model),
 * and gpt-4o-transcribe-diarize are supported. <br/>
 * You can find description of parameters
 * <a href="https://platform.openai.com/docs/api-reference/audio/createTranscription">here</a>.
 */
@Experimental
public class OpenAiAudioModel implements AudioTranscriptionModel {

    private final OpenAiClient client;
    private final Integer maxRetries;
    private final String modelName;

    public OpenAiAudioModel(OpenAiAudioModelBuilder builder) {
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
            throw new IllegalArgumentException("Request and audio data are required");
        }

        validate(audioRequest);

        OpenAiAudioTranscriptionRequest openAiRequest =
                requestBuilder(audioRequest).build();

        ParsedAndRawResponse<OpenAiAudioTranscriptionResponse> parsedAndRawResponse = withRetryMappingExceptions(
                () -> client.audioTranscription(openAiRequest).executeRaw(), maxRetries);

        var openAiResponse = parsedAndRawResponse.parsedResponse();

        return AudioTranscriptionResponse.from(openAiResponse.text());
    }

    private OpenAiAudioTranscriptionRequest.Builder requestBuilder(AudioTranscriptionRequest request) {
        return OpenAiAudioTranscriptionRequest.builder()
                .model(modelName)
                .file(request.audio())
                .language(request.language())
                .prompt(request.prompt())
                .temperature(request.temperature())
                .temperature(request.temperature());
    }

    @Override
    public ModelProvider provider() {
        return OPEN_AI;
    }

    public static OpenAiAudioModelBuilder builder() {
        for (OpenAiAudioModelBuilderFactory factory : loadFactories(OpenAiAudioModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiAudioModelBuilder();
    }

    public static class OpenAiAudioModelBuilder {

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

        public OpenAiAudioModelBuilder() {
            // This is public so it can be extended
        }

        public OpenAiAudioModelBuilder httpClientProvider(HttpClientBuilder httpClientProvider) {
            this.httpClientBuilder = httpClientProvider;
            return this;
        }

        public OpenAiAudioModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiAudioModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiAudioModelBuilder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public OpenAiAudioModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiAudioModelBuilder modelName(OpenAiAudioModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public OpenAiAudioModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiAudioModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiAudioModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OpenAiAudioModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OpenAiAudioModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public OpenAiAudioModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public OpenAiAudioModel build() {
            return new OpenAiAudioModel(this);
        }
    }
}
