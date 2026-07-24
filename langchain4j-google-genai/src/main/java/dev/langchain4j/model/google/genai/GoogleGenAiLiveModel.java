package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.AsyncSession;
import com.google.genai.Client;
import com.google.genai.types.AudioTranscriptionConfig;
import com.google.genai.types.Content;
import com.google.genai.types.LiveConnectConfig;
import com.google.genai.types.Part;
import com.google.genai.types.PrebuiltVoiceConfig;
import com.google.genai.types.SpeechConfig;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.VoiceConfig;
import dev.langchain4j.Experimental;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Entry point for the Google Gemini <a href="https://ai.google.dev/gemini-api/docs/live">Live API</a>,
 * built on the official {@code com.google.genai} SDK.
 *
 * <p>The Live API is a stateful, bidirectional, real-time interface: text and audio are streamed to the
 * model over a persistent connection and text or audio is streamed back. It does not fit the
 * request/response {@code ChatModel} contract, so it is exposed as its own session type.
 *
 * <p>Configure a model with the {@link Builder}, then open a session with
 * {@link #connect(GoogleGenAiLiveResponseHandler)}:
 *
 * <pre>{@code
 * GoogleGenAiLiveModel model = GoogleGenAiLiveModel.builder()
 *         .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
 *         .modelName("gemini-2.5-flash-native-audio-latest")
 *         .responseModalities("AUDIO")
 *         .outputAudioTranscription(true)
 *         .build();
 *
 * try (GoogleGenAiLiveSession session = model.connect(new GoogleGenAiLiveResponseHandler() {
 *     public void onAudio(byte[] pcm) { } // play the 24 kHz PCM audio
 *     public void onOutputTranscription(String text) { System.out.print(text); }
 * })) {
 *     session.sendText("Say hello in one short sentence.");
 *     // ... handle events until onTurnComplete
 * }
 * }</pre>
 *
 * <p>A session supports exactly one response modality ({@code TEXT} or {@code AUDIO}); native-audio models
 * require {@code AUDIO}. For a text view of an audio response, enable output audio transcription.
 */
@Experimental
public final class GoogleGenAiLiveModel {

    private final Client client;
    private final String modelName;
    private final List<String> responseModalities;
    private final String systemInstruction;
    private final Double temperature;
    private final Double topP;
    private final Double topK;
    private final Integer maxOutputTokens;
    private final Integer seed;
    private final String voiceName;
    private final boolean inputAudioTranscription;
    private final boolean outputAudioTranscription;
    private final Integer thinkingBudget;
    private final String thinkingLevel;
    private final LiveConnectConfig liveConnectConfig;

    private GoogleGenAiLiveModel(Builder builder) {
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.responseModalities = builder.responseModalities == null || builder.responseModalities.isEmpty()
                ? List.of("TEXT")
                : List.copyOf(builder.responseModalities);
        if (this.responseModalities.size() != 1) {
            throw new IllegalArgumentException(
                    "The Live API supports exactly one response modality per session (\"TEXT\" or \"AUDIO\"), but got: "
                            + this.responseModalities);
        }
        this.systemInstruction = builder.systemInstruction;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.seed = builder.seed;
        this.voiceName = builder.voiceName;
        this.inputAudioTranscription = builder.inputAudioTranscription;
        this.outputAudioTranscription = builder.outputAudioTranscription;
        this.thinkingBudget = builder.thinkingBudget;
        this.thinkingLevel = builder.thinkingLevel;
        this.liveConnectConfig = builder.liveConnectConfig;
        this.client = builder.client != null
                ? builder.client
                : GoogleGenAiClientFactory.createClient(
                        builder.apiKey,
                        builder.googleCredentials,
                        builder.projectId,
                        builder.location,
                        builder.timeout,
                        builder.customHeaders,
                        builder.apiEndpoint);
    }

    /**
     * Opens a live session and registers the handler that receives its events.
     *
     * @param handler the handler invoked for every server event on the session
     * @return an open {@link GoogleGenAiLiveSession}
     */
    public GoogleGenAiLiveSession connect(GoogleGenAiLiveResponseHandler handler) {
        ensureNotNull(handler, "handler");
        try {
            AsyncSession session =
                    client.async.live.connect(modelName, buildConfig()).get();
            return new GoogleGenAiLiveSessionImpl(session, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw GoogleGenAiExceptionMapper.INSTANCE.mapException(e);
        } catch (ExecutionException e) {
            throw GoogleGenAiExceptionMapper.INSTANCE.mapException(e.getCause() != null ? e.getCause() : e);
        }
    }

    LiveConnectConfig buildConfig() {
        LiveConnectConfig.Builder config =
                liveConnectConfig != null ? liveConnectConfig.toBuilder() : LiveConnectConfig.builder();
        config.responseModalities(responseModalities.toArray(new String[0]));
        if (systemInstruction != null) {
            config.systemInstruction(Content.fromParts(Part.fromText(systemInstruction)));
        }
        if (temperature != null) {
            config.temperature(temperature.floatValue());
        }
        if (topP != null) {
            config.topP(topP.floatValue());
        }
        if (topK != null) {
            config.topK(topK.floatValue());
        }
        if (maxOutputTokens != null) {
            config.maxOutputTokens(maxOutputTokens);
        }
        if (seed != null) {
            config.seed(seed);
        }
        if (voiceName != null) {
            config.speechConfig(SpeechConfig.builder()
                    .voiceConfig(VoiceConfig.builder()
                            .prebuiltVoiceConfig(PrebuiltVoiceConfig.builder()
                                    .voiceName(voiceName)
                                    .build())
                            .build())
                    .build());
        }
        if (inputAudioTranscription) {
            config.inputAudioTranscription(AudioTranscriptionConfig.builder().build());
        }
        if (outputAudioTranscription) {
            config.outputAudioTranscription(AudioTranscriptionConfig.builder().build());
        }
        if (thinkingBudget != null || thinkingLevel != null) {
            ThinkingConfig.Builder thinking = ThinkingConfig.builder();
            if (thinkingBudget != null) {
                thinking.thinkingBudget(thinkingBudget);
            }
            if (thinkingLevel != null) {
                thinking.thinkingLevel(thinkingLevel);
            }
            config.thinkingConfig(thinking.build());
        }
        return config.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Client client;
        private String apiKey;
        private GoogleCredentials googleCredentials;
        private String projectId;
        private String location;
        private Duration timeout;
        private Map<String, String> customHeaders;
        private String apiEndpoint;
        private String modelName;
        private List<String> responseModalities;
        private String systemInstruction;
        private Double temperature;
        private Double topP;
        private Double topK;
        private Integer maxOutputTokens;
        private Integer seed;
        private String voiceName;
        private boolean inputAudioTranscription;
        private boolean outputAudioTranscription;
        private Integer thinkingBudget;
        private String thinkingLevel;
        private LiveConnectConfig liveConnectConfig;

        /** A pre-built {@code com.google.genai.Client}. If set, the auth options below are ignored. */
        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        /** The Gemini Developer API key. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** Google credentials for the Vertex AI backend. */
        public Builder googleCredentials(GoogleCredentials googleCredentials) {
            this.googleCredentials = googleCredentials;
            return this;
        }

        /** The Google Cloud project id for the Vertex AI backend. */
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        /** The Google Cloud location for the Vertex AI backend. */
        public Builder location(String location) {
            this.location = location;
            return this;
        }

        /** The connection timeout. */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** Custom HTTP headers to send with requests. */
        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        /** A custom API endpoint (base URL). */
        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        /** The Live-capable model name, e.g. {@code "gemini-2.0-flash-live-001"}. Required. */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * The single response modality for the session: {@code "TEXT"} or {@code "AUDIO"}. Defaults to
         * {@code "TEXT"}. Exactly one modality is allowed per session.
         */
        public Builder responseModalities(String... responseModalities) {
            this.responseModalities = List.of(responseModalities);
            return this;
        }

        /** The system instruction that guides the model's behavior. */
        public Builder systemInstruction(String systemInstruction) {
            this.systemInstruction = systemInstruction;
            return this;
        }

        /** The sampling temperature. */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /** The nucleus sampling probability. */
        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /** The top-k sampling value. */
        public Builder topK(Double topK) {
            this.topK = topK;
            return this;
        }

        /** The maximum number of output tokens. */
        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        /** The random seed for reproducible sampling. */
        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        /** The prebuilt voice name for audio output, e.g. {@code "Puck"} or {@code "Kore"}. */
        public Builder voiceName(String voiceName) {
            this.voiceName = voiceName;
            return this;
        }

        /** Enables transcription of the user's input audio, delivered via {@code onInputTranscription}. */
        public Builder inputAudioTranscription(boolean inputAudioTranscription) {
            this.inputAudioTranscription = inputAudioTranscription;
            return this;
        }

        /** Enables transcription of the model's output audio, delivered via {@code onOutputTranscription}. */
        public Builder outputAudioTranscription(boolean outputAudioTranscription) {
            this.outputAudioTranscription = outputAudioTranscription;
            return this;
        }

        /** The thinking token budget (Gemini 2.5 models); {@code 0} disables thinking. */
        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        /** The thinking level (Gemini 3.x models), e.g. {@code "minimal"}, {@code "low"}, {@code "high"}. */
        public Builder thinkingLevel(String thinkingLevel) {
            this.thinkingLevel = thinkingLevel;
            return this;
        }

        /**
         * An advanced escape hatch: a base {@link LiveConnectConfig} used as the starting point for the
         * session config, so options not surfaced as first-class builder methods (for example
         * {@code sessionResumption}, {@code contextWindowCompression}, {@code mediaResolution},
         * {@code safetySettings} or {@code translationConfig}) remain reachable. Any first-class option set on
         * this builder takes precedence over the same field in the passed config.
         */
        public Builder liveConnectConfig(LiveConnectConfig liveConnectConfig) {
            this.liveConnectConfig = liveConnectConfig;
            return this;
        }

        public GoogleGenAiLiveModel build() {
            return new GoogleGenAiLiveModel(this);
        }
    }
}
