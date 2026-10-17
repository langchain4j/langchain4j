package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
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
import dev.langchain4j.model.openai.internal.audio.texttospeech.OpenAiVoice;
import dev.langchain4j.model.openai.spi.OpenAiTextToSpeechModelBuilderFactory;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Represents an OpenAI text-to-speech model with a speech generation interface.<br/>
 * You can find a description of the parameters
 * <a href="https://platform.openai.com/docs/api-reference/audio/createSpeech">here</a>.
 *
 * @since 1.18.0
 */
@Experimental
public class OpenAiTextToSpeechModel implements TextToSpeechModel {

    static final int MAX_INPUT_TEXT_LENGTH = 4096;
    static final int MAX_INSTRUCTIONS_LENGTH = 4096;
    static final double MIN_SPEED = 0.25;
    static final double MAX_SPEED = 4.0;
    private static final Set<String> MODELS_WITHOUT_INSTRUCTIONS =
            Set.of(OpenAiTextToSpeechModelName.TTS_1.toString(), OpenAiTextToSpeechModelName.TTS_1_HD.toString());

    private final OpenAiClient client;
    private final int maxRetries;
    private final String modelName;
    private final OpenAiVoice voice;
    private final String instructions;
    private final OpenAiTextToSpeechResponseFormat responseFormat;
    private final Double speed;

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
        this.voice = getOrDefault(builder.voice, OpenAiVoice.BuiltIn.ALLOY);
        if (builder.instructions != null && MODELS_WITHOUT_INSTRUCTIONS.contains(modelName)) {
            throw new IllegalArgumentException("Model '" + modelName + "' does not support instructions. "
                    + "Use a model such as gpt-4o-mini-tts instead.");
        }
        this.instructions = ensureMaxLength(builder.instructions, MAX_INSTRUCTIONS_LENGTH, "instructions");
        this.responseFormat = builder.responseFormat;
        this.speed = builder.speed == null ? null : ensureBetween(builder.speed, MIN_SPEED, MAX_SPEED, "speed");
    }

    @Override
    public TextToSpeechResponse synthesize(TextToSpeechRequest textToSpeechRequest) {
        if (textToSpeechRequest == null) {
            throw new IllegalArgumentException("Request is required");
        }
        ensureMaxLength(textToSpeechRequest.text(), MAX_INPUT_TEXT_LENGTH, "Input text");

        OpenAiTextToSpeechRequest openAiRequest =
                requestBuilder(textToSpeechRequest).build();

        OpenAiTextToSpeechResponse openAiResponse = withRetryMappingExceptions(
                () -> client.textToSpeech(openAiRequest).execute(), maxRetries);

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
                .voice(request.voice() != null ? toApiVoice(request.voice()) : voice)
                .instructions(instructions)
                .responseFormat(responseFormat == null ? null : responseFormat.toString())
                .speed(speed);
    }

    static String ensureMaxLength(String value, int maxLength, String name) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds the maximum length of " + maxLength + " characters");
        }
        return value;
    }

    /**
     * Resolves the voice of a {@link TextToSpeechRequest}: a built-in voice name (e.g. {@code coral})
     * is sent as a built-in voice, any other value is sent as a custom voice ID (e.g. {@code voice_1234}).
     */
    static OpenAiVoice toApiVoice(String voice) {
        for (OpenAiTextToSpeechVoiceName voiceName : OpenAiTextToSpeechVoiceName.values()) {
            if (voiceName.toString().equals(voice)) {
                return toApiVoice(voiceName);
            }
        }
        return OpenAiVoice.custom(OpenAiCustomVoice.from(voice).id());
    }

    static OpenAiVoice.BuiltIn toApiVoice(OpenAiTextToSpeechVoiceName voiceName) {
        return switch (voiceName) {
            case ALLOY -> OpenAiVoice.BuiltIn.ALLOY;
            case ASH -> OpenAiVoice.BuiltIn.ASH;
            case BALLAD -> OpenAiVoice.BuiltIn.BALLAD;
            case CORAL -> OpenAiVoice.BuiltIn.CORAL;
            case ECHO -> OpenAiVoice.BuiltIn.ECHO;
            case FABLE -> OpenAiVoice.BuiltIn.FABLE;
            case ONYX -> OpenAiVoice.BuiltIn.ONYX;
            case NOVA -> OpenAiVoice.BuiltIn.NOVA;
            case SAGE -> OpenAiVoice.BuiltIn.SAGE;
            case SHIMMER -> OpenAiVoice.BuiltIn.SHIMMER;
            case VERSE -> OpenAiVoice.BuiltIn.VERSE;
            case MARIN -> OpenAiVoice.BuiltIn.MARIN;
            case CEDAR -> OpenAiVoice.BuiltIn.CEDAR;
        };
    }

    @Override
    public ModelProvider provider() {
        return OPEN_AI;
    }

    public static Builder builder() {
        for (OpenAiTextToSpeechModelBuilderFactory factory :
                loadFactories(OpenAiTextToSpeechModelBuilderFactory.class)) {
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
        private OpenAiVoice voice;
        private String instructions;
        private OpenAiTextToSpeechResponseFormat responseFormat;
        private Double speed;

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

        /**
         * Sets the default voice: a built-in voice name (e.g. {@code alloy}) is used as a built-in voice,
         * any other value is used as a custom voice ID (e.g. {@code voice_1234}).
         * It is used when the {@link TextToSpeechRequest} does not specify a voice.
         *
         * @throws IllegalArgumentException if the voice is blank
         * @see OpenAiTextToSpeechVoiceName
         * @see OpenAiCustomVoice
         */
        public Builder voice(String voice) {
            this.voice = voice == null ? null : toApiVoice(voice);
            return this;
        }

        /**
         * Sets the default built-in voice.
         * It is used when the {@link TextToSpeechRequest} does not specify a voice.
         */
        public Builder voice(OpenAiTextToSpeechVoiceName voice) {
            this.voice = voice == null ? null : toApiVoice(voice);
            return this;
        }

        /**
         * Sets a custom voice as the default voice.
         * It is used when the {@link TextToSpeechRequest} does not specify a voice.
         */
        public Builder voice(OpenAiCustomVoice voice) {
            this.voice = voice == null ? null : OpenAiVoice.custom(voice.id());
            return this;
        }

        /**
         * Controls the voice of the generated audio with additional instructions (at most 4096 characters).
         * Does not work with tts-1 or tts-1-hd.
         */
        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        /**
         * Sets the format of the generated audio. The API default is mp3.
         */
        public Builder responseFormat(OpenAiTextToSpeechResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        /**
         * Sets the speed of the generated audio, from 0.25 to 4.0. The API default is 1.0.
         */
        public Builder speed(Double speed) {
            this.speed = speed;
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
