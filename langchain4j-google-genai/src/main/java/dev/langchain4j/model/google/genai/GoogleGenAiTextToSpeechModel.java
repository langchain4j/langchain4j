package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.google.genai.GoogleGenAiContentMapper.mapFinishReason;
import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.Blob;
import com.google.genai.types.BlockedReason;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.MultiSpeakerVoiceConfig;
import com.google.genai.types.Part;
import com.google.genai.types.PrebuiltVoiceConfig;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.SpeakerVoiceConfig;
import com.google.genai.types.SpeechConfig;
import com.google.genai.types.SpeechMetadata;
import com.google.genai.types.VoiceConfig;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.audio.TextToSpeechModel;
import dev.langchain4j.model.audio.TextToSpeechRequest;
import dev.langchain4j.model.audio.TextToSpeechResponse;
import dev.langchain4j.model.output.FinishReason;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a Google GenAI text-to-speech model, such as {@code gemini-3.8-flash-tts}, using the official
 * com.google.genai SDK.
 * <pre>{@code
 * TextToSpeechModel model = GoogleGenAiTextToSpeechModel.builder()
 *         .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
 *         .modelName("gemini-3.8-flash-tts")
 *         .voice("Kore")
 *         .build();
 *
 * TextToSpeechResponse response = model.synthesize("Have a wonderful day!");
 * }</pre>
 * Raw 16-bit PCM audio ({@code audio/L16} or {@code audio/pcm}) is wrapped in a WAV header built from the sample
 * rate and channel count of its MIME type, so that it can be played or saved as a WAV file. Any other audio, such as
 * the WAV returned by the {@code gemini-3.8} text-to-speech models, is returned unchanged.
 * <p>
 * With {@link Builder#speaker(String, String)} or {@link Builder#multiSpeakerVoiceConfig(MultiSpeakerVoiceConfig)},
 * the text is read as a conversation in which
 * every turn starts with the name of a configured speaker followed by a colon, for example
 * {@code "Joe: How is it going today, Jane?\nJane: Not too bad, how about you?"}, and every turn is spoken in the
 * voice of its speaker. Text before the first turn is rejected with an {@link IllegalArgumentException}.
 *
 * @since 1.23.0
 */
@Experimental
public class GoogleGenAiTextToSpeechModel implements TextToSpeechModel {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiTextToSpeechModel.class);

    private static final int WAV_HEADER_SIZE = 44;
    private static final int FMT_CHUNK_SIZE = 16;
    private static final short PCM_AUDIO_FORMAT = 1;
    private static final short BITS_PER_SAMPLE = 16;

    private final Client client;
    private final String modelName;
    private final int maxRetries;
    private final String voice;
    private final String languageCode;
    private final MultiSpeakerVoiceConfig multiSpeakerVoiceConfig;
    private final List<SafetySetting> safetySettings;
    private final Map<String, String> labels;
    private final boolean logRequests;
    private final boolean logResponses;

    private GoogleGenAiTextToSpeechModel(Builder builder) {
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

        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
        this.voice = builder.voice;
        this.languageCode = builder.languageCode;
        this.multiSpeakerVoiceConfig = builder.multiSpeakerVoiceConfig;
        validateVoice(builder.voice);
        this.safetySettings = copy(builder.safetySettings);
        this.labels = builder.labels != null ? new HashMap<>(builder.labels) : null;
        this.logRequests = getOrDefault(builder.logRequests, false);
        this.logResponses = getOrDefault(builder.logResponses, false);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public TextToSpeechResponse synthesize(TextToSpeechRequest request) {
        ensureNotNull(request, "request");
        validateVoice(request.voice());

        List<Part> parts = toParts(request.text());
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("The text does not contain any speech turns to synthesize");
        }
        List<Content> contents =
                List.of(Content.builder().role("user").parts(parts).build());
        GenerateContentConfig config = createGenerateContentConfig(request);

        if (logRequests) {
            log.info("Request:\n- model: {}\n- contents: {}\n- config: {}", modelName, contents, config);
        }

        GenerateContentResponse response = withRetryMappingExceptions(
                () -> client.models.generateContent(modelName, contents, config),
                maxRetries,
                GoogleGenAiExceptionMapper.INSTANCE);

        Audio audio = toAudio(response);

        if (logResponses) {
            log.info(
                    "Response:\n- model: {}\n- audio: {} ({} bytes)",
                    modelName,
                    audio.mimeType(),
                    audio.binaryData().length);
        }

        return TextToSpeechResponse.from(audio);
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.GOOGLE_GENAI;
    }

    private void validateVoice(String voice) {
        if (voice != null && multiSpeakerVoiceConfig != null) {
            throw new IllegalArgumentException(
                    "A voice cannot be combined with a multi-speaker configuration, set the voice of each speaker instead");
        }
    }

    private List<Part> toParts(String text) {
        if (multiSpeakerVoiceConfig == null) {
            return List.of(Part.fromText(text));
        }

        List<String> speakers = multiSpeakerVoiceConfig.speakerVoiceConfigs().orElse(List.of()).stream()
                .map(speakerVoiceConfig -> speakerVoiceConfig.speaker().orElse(null))
                .filter(Objects::nonNull)
                .toList();

        List<Part> parts = new ArrayList<>();
        String speaker = null;
        StringBuilder turn = new StringBuilder();
        for (String line : text.split("\\R")) {
            String stripped = line.stripLeading();
            String lineSpeaker = speakerOf(stripped, speakers);
            if (lineSpeaker != null) {
                addTurn(parts, speaker, turn);
                speaker = lineSpeaker;
                turn = new StringBuilder(stripped.substring(lineSpeaker.length() + 1));
            } else {
                if (speaker == null && !line.isBlank()) {
                    throw new IllegalArgumentException(
                            "The text must start with the name of a configured speaker followed by a colon (configured speakers: "
                                    + String.join(", ", speakers) + ")");
                }
                if (!turn.isEmpty()) {
                    turn.append('\n');
                }
                turn.append(line);
            }
        }
        addTurn(parts, speaker, turn);
        return parts;
    }

    private static String speakerOf(String stripped, List<String> speakers) {
        for (String speaker : speakers) {
            if (stripped.startsWith(speaker + ":")) {
                return speaker;
            }
        }
        return null;
    }

    private static void addTurn(List<Part> parts, String speaker, StringBuilder turn) {
        String text = turn.toString().strip();
        if (text.isEmpty()) {
            return;
        }
        Part.Builder part = Part.builder().text(text);
        if (speaker != null) {
            part.speechMetadata(SpeechMetadata.builder().speaker(speaker));
        }
        parts.add(part.build());
    }

    private GenerateContentConfig createGenerateContentConfig(TextToSpeechRequest request) {
        GenerateContentConfig.Builder configBuilder =
                GenerateContentConfig.builder().responseModalities(List.of("AUDIO"));

        String effectiveVoice = getOrDefault(request.voice(), voice);
        if (effectiveVoice != null || languageCode != null || multiSpeakerVoiceConfig != null) {
            SpeechConfig.Builder speechConfigBuilder = SpeechConfig.builder();
            if (effectiveVoice != null) {
                speechConfigBuilder.voiceConfig(VoiceConfig.builder()
                        .prebuiltVoiceConfig(PrebuiltVoiceConfig.builder().voiceName(effectiveVoice)));
            }
            if (languageCode != null) {
                speechConfigBuilder.languageCode(languageCode);
            }
            if (multiSpeakerVoiceConfig != null) {
                speechConfigBuilder.multiSpeakerVoiceConfig(multiSpeakerVoiceConfig);
            }
            configBuilder.speechConfig(speechConfigBuilder.build());
        }

        if (!safetySettings.isEmpty()) {
            configBuilder.safetySettings(safetySettings);
        }

        if (labels != null && !labels.isEmpty()) {
            configBuilder.labels(labels);
        }

        return configBuilder.build();
    }

    private static Audio toAudio(GenerateContentResponse response) {
        for (Part part : partsOf(response)) {
            Optional<Blob> blob = part.inlineData();
            if (blob.isPresent() && blob.get().data().isPresent()) {
                return toAudio(blob.get());
            }
        }
        throw noAudioException(response);
    }

    private static Audio toAudio(Blob blob) {
        byte[] data = blob.data().get();
        String mimeType = blob.mimeType().orElse(null);
        if (mimeType != null) {
            String[] tokens = mimeType.split(";");
            String type = tokens[0].strip().toLowerCase(Locale.ROOT);
            if (type.equals("audio/l16") || type.equals("audio/pcm")) {
                Integer sampleRate = null;
                Integer channels = 1;
                for (int i = 1; i < tokens.length; i++) {
                    String[] parameter = tokens[i].split("=", 2);
                    if (parameter.length == 2) {
                        String name = parameter[0].strip().toLowerCase(Locale.ROOT);
                        if (name.equals("rate")) {
                            sampleRate = parsePositiveInt(parameter[1]);
                        } else if (name.equals("channels")) {
                            channels = parsePositiveInt(parameter[1]);
                        }
                    }
                }
                if (sampleRate != null && channels != null) {
                    return Audio.builder()
                            .binaryData(toWav(data, sampleRate, channels))
                            .mimeType("audio/wav")
                            .build();
                }
            }
        }
        return Audio.builder().binaryData(data).mimeType(mimeType).build();
    }

    private static Integer parsePositiveInt(String value) {
        try {
            String trimmed = value.strip();
            if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).strip();
            }
            int parsed = Integer.parseInt(trimmed);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static byte[] toWav(byte[] pcm, int sampleRate, int channels) {
        int blockAlign = channels * BITS_PER_SAMPLE / 8;
        return ByteBuffer.allocate(WAV_HEADER_SIZE + pcm.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes(US_ASCII))
                .putInt(WAV_HEADER_SIZE - 8 + pcm.length)
                .put("WAVE".getBytes(US_ASCII))
                .put("fmt ".getBytes(US_ASCII))
                .putInt(FMT_CHUNK_SIZE)
                .putShort(PCM_AUDIO_FORMAT)
                .putShort((short) channels)
                .putInt(sampleRate)
                .putInt(sampleRate * blockAlign)
                .putShort((short) blockAlign)
                .putShort(BITS_PER_SAMPLE)
                .put("data".getBytes(US_ASCII))
                .putInt(pcm.length)
                .put(pcm)
                .array();
    }

    private static Optional<Candidate> firstCandidate(GenerateContentResponse response) {
        return response.candidates().flatMap(candidates -> candidates.stream().findFirst());
    }

    private static List<Part> partsOf(GenerateContentResponse response) {
        return firstCandidate(response)
                .flatMap(Candidate::content)
                .flatMap(Content::parts)
                .orElse(List.of());
    }

    private static LangChain4jException noAudioException(GenerateContentResponse response) {
        Optional<BlockedReason> blockReason =
                response.promptFeedback().flatMap(GenerateContentResponsePromptFeedback::blockReason);
        if (blockReason.isPresent()) {
            return new ContentFilteredException("The text was blocked: " + blockReason.get());
        }

        com.google.genai.types.FinishReason finishReason =
                firstCandidate(response).flatMap(Candidate::finishReason).orElse(null);
        String text = partsOf(response).stream()
                .filter(part -> !part.thought().orElse(false))
                .map(part -> part.text().orElse(""))
                .collect(Collectors.joining());
        String message = "The response contains no audio, finish reason: " + finishReason
                + (text.isBlank() ? "" : ", text: " + text);
        if (mapFinishReason(finishReason) == FinishReason.CONTENT_FILTER) {
            return new ContentFilteredException(message);
        }
        return new LangChain4jException(message);
    }

    public static class Builder {
        private Client client;
        private String apiKey;
        private GoogleCredentials googleCredentials;
        private String projectId;
        private String location;
        private Duration timeout;
        private String modelName;
        private Integer maxRetries;
        private String voice;
        private String languageCode;
        private MultiSpeakerVoiceConfig multiSpeakerVoiceConfig;
        private List<SafetySetting> safetySettings;
        private Boolean logRequests;
        private Boolean logResponses;
        private String apiEndpoint;
        private Map<String, String> customHeaders;
        private Map<String, String> labels;

        /**
         * Sets the Google GenAI client to use. When set, the authentication and transport options of this builder
         * are ignored.
         *
         * @param client the client
         * @return {@code this}
         */
        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        /**
         * Sets the API key of the Gemini Developer API.
         *
         * @param apiKey the API key
         * @return {@code this}
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the credentials used to call Vertex AI.
         *
         * @param googleCredentials the credentials
         * @return {@code this}
         */
        public Builder googleCredentials(GoogleCredentials googleCredentials) {
            this.googleCredentials = googleCredentials;
            return this;
        }

        /**
         * Sets the Google Cloud project used to call Vertex AI.
         *
         * @param projectId the project ID
         * @return {@code this}
         */
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        /**
         * Sets the Google Cloud location used to call Vertex AI.
         *
         * @param location the location, for example {@code global}
         * @return {@code this}
         */
        public Builder location(String location) {
            this.location = location;
            return this;
        }

        /**
         * Sets the timeout of each HTTP request.
         *
         * @param timeout the timeout
         * @return {@code this}
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the text-to-speech model to use, for example {@code gemini-3.8-flash-tts}. Required.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets how many times a failed request is retried. Defaults to 3.
         *
         * @param maxRetries the maximum number of retries
         * @return {@code this}
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the voice to use when a {@link TextToSpeechRequest} does not specify one, for example {@code Kore} or
         * {@code Puck}. When no voice is set at all, the API chooses one.
         *
         * @param voice the name of a prebuilt voice
         * @return {@code this}
         */
        public Builder voice(String voice) {
            this.voice = voice;
            return this;
        }

        /**
         * Sets the language of the speech as a BCP-47 code, for example {@code de-DE}.
         * When it is not set, the language is detected from the text.
         *
         * @param languageCode the language code
         * @return {@code this}
         */
        public Builder languageCode(String languageCode) {
            this.languageCode = languageCode;
            return this;
        }

        /**
         * Sets the voice of each speaker of a conversation. The text is then split into turns: a line that starts
         * with the name of a configured speaker followed by a colon, such as {@code "Joe: Hello"}, starts a new turn
         * spoken in that speaker's voice, and the following lines without a speaker name belong to the same turn.
         * The text must start with a turn: text before the first one is rejected with an
         * {@link IllegalArgumentException}.
         * <p>
         * It requires a model that accepts a speaker per turn, such as {@code gemini-3.8-flash-tts}, and cannot be
         * combined with a voice set with {@link #voice(String)} or on the {@link TextToSpeechRequest}.
         *
         * @param multiSpeakerVoiceConfig the voices of the speakers
         * @return {@code this}
         */
        public Builder multiSpeakerVoiceConfig(MultiSpeakerVoiceConfig multiSpeakerVoiceConfig) {
            this.multiSpeakerVoiceConfig = multiSpeakerVoiceConfig;
            return this;
        }

        /**
         * Adds a speaker of a conversation and the prebuilt voice it speaks with, for example
         * {@code speaker("Joe", "Kore")}. Call it once per speaker; it is a shorter way to build the
         * {@link #multiSpeakerVoiceConfig(MultiSpeakerVoiceConfig)}, and adds to one that is already set.
         *
         * @param speaker the name that starts the turns of this speaker in the text
         * @param voice the name of a prebuilt voice
         * @return {@code this}
         */
        public Builder speaker(String speaker, String voice) {
            List<SpeakerVoiceConfig> speakerVoiceConfigs = new ArrayList<>();
            if (multiSpeakerVoiceConfig != null) {
                speakerVoiceConfigs.addAll(
                        multiSpeakerVoiceConfig.speakerVoiceConfigs().orElse(List.of()));
            }
            speakerVoiceConfigs.add(SpeakerVoiceConfig.builder()
                    .speaker(ensureNotBlank(speaker, "speaker"))
                    .voiceConfig(VoiceConfig.builder()
                            .prebuiltVoiceConfig(
                                    PrebuiltVoiceConfig.builder().voiceName(ensureNotBlank(voice, "voice"))))
                    .build());
            this.multiSpeakerVoiceConfig = MultiSpeakerVoiceConfig.builder()
                    .speakerVoiceConfigs(speakerVoiceConfigs)
                    .build();
            return this;
        }

        /**
         * Sets the safety settings applied to the text.
         *
         * @param safetySettings the safety settings
         * @return {@code this}
         */
        public Builder safetySettings(List<SafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        /**
         * Enables logging of the requests sent to the API.
         *
         * @param logRequests whether to log requests
         * @return {@code this}
         */
        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables logging of the responses received from the API. The audio itself is not logged, only its MIME
         * type and size.
         *
         * @param logResponses whether to log responses
         * @return {@code this}
         */
        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * Enables logging of both the requests and the responses.
         *
         * @param logRequestsAndResponses whether to log requests and responses
         * @return {@code this}
         */
        public Builder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequests = logRequestsAndResponses;
            this.logResponses = logRequestsAndResponses;
            return this;
        }

        /**
         * Sets the base URL of the API.
         *
         * @param apiEndpoint the base URL
         * @return {@code this}
         */
        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        /**
         * Sets HTTP headers added to every request.
         *
         * @param customHeaders the headers
         * @return {@code this}
         */
        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        /**
         * Sets labels attached to every request, used by Vertex AI to break down billing.
         *
         * @param labels the labels
         * @return {@code this}
         */
        public Builder labels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public GoogleGenAiTextToSpeechModel build() {
            return new GoogleGenAiTextToSpeechModel(this);
        }
    }
}
