package dev.langchain4j.model.google.genai;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.errors.ClientException;
import com.google.genai.types.BlockedReason;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.HarmBlockThreshold;
import com.google.genai.types.HarmCategory;
import com.google.genai.types.MultiSpeakerVoiceConfig;
import com.google.genai.types.Part;
import com.google.genai.types.PrebuiltVoiceConfig;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.SpeakerVoiceConfig;
import com.google.genai.types.SpeechMetadata;
import com.google.genai.types.VoiceConfig;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.audio.TextToSpeechRequest;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class GoogleGenAiTextToSpeechModelTest {

    private static final String MODEL_NAME = "gemini-3.8-flash-tts";
    private static final byte[] AUDIO_BYTES = {1, 2, 3, 4};

    private static final MultiSpeakerVoiceConfig JOE_AND_JANE = MultiSpeakerVoiceConfig.builder()
            .speakerVoiceConfigs(speaker("Joe", "Kore"), speaker("Jane", "Puck"))
            .build();

    private final Models models = mock(Models.class);
    private final ArgumentCaptor<List<Content>> contentsCaptor = ArgumentCaptor.captor();
    private final ArgumentCaptor<GenerateContentConfig> configCaptor = ArgumentCaptor.captor();

    @Test
    void should_request_audio_with_configured_voice() throws Exception {
        returnAudio("audio/wav");

        modelBuilder().voice("Kore").build().synthesize("Hello");

        GenerateContentConfig config = configCaptor.getValue();
        assertThat(config.responseModalities()).contains(List.of("AUDIO"));
        assertThat(voiceName(config)).isEqualTo("Kore");
    }

    @Test
    void should_use_request_voice_over_configured_voice() throws Exception {
        returnAudio("audio/wav");

        modelBuilder()
                .voice("Kore")
                .build()
                .synthesize(TextToSpeechRequest.builder()
                        .text("Hello")
                        .voice("Puck")
                        .build());

        assertThat(voiceName(configCaptor.getValue())).isEqualTo("Puck");
    }

    @Test
    void should_not_send_speech_config_when_no_voice_language_or_speakers_are_set() throws Exception {
        returnAudio("audio/wav");

        modelBuilder().build().synthesize("Hello");

        assertThat(configCaptor.getValue().speechConfig()).isEmpty();
    }

    @Test
    void should_send_language_code() throws Exception {
        returnAudio("audio/wav");

        modelBuilder().languageCode("de-DE").build().synthesize("Guten Morgen");

        GenerateContentConfig config = configCaptor.getValue();
        assertThat(config.speechConfig().get().languageCode()).contains("de-DE");
        assertThat(config.speechConfig().get().voiceConfig()).isEmpty();
    }

    @Test
    void should_send_text_as_single_part() throws Exception {
        returnAudio("audio/wav");

        modelBuilder().build().synthesize("Hello world");

        List<Part> parts = parts();
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).text()).contains("Hello world");
        assertThat(parts.get(0).speechMetadata()).isEmpty();
    }

    @Test
    void should_send_text_with_user_role() throws Exception {
        returnAudio("audio/wav");

        modelBuilder().build().synthesize("Hello world");

        assertThat(contentsCaptor.getValue().get(0).role()).contains("user");
    }

    @Test
    void should_send_safety_settings_and_labels() throws Exception {
        returnAudio("audio/wav");
        SafetySetting safetySetting = SafetySetting.builder()
                .category(HarmCategory.Known.HARM_CATEGORY_HARASSMENT)
                .threshold(HarmBlockThreshold.Known.BLOCK_LOW_AND_ABOVE)
                .build();

        modelBuilder()
                .safetySettings(List.of(safetySetting))
                .labels(Map.of("team", "voice"))
                .build()
                .synthesize("Hello");

        GenerateContentConfig config = configCaptor.getValue();
        assertThat(config.safetySettings()).contains(List.of(safetySetting));
        assertThat(config.labels()).contains(Map.of("team", "voice"));
    }

    @Test
    void should_return_wav_unchanged() throws Exception {
        returnAudio("audio/wav");

        Audio audio = modelBuilder().build().synthesize("Hello").audio();

        assertThat(audio.binaryData()).isEqualTo(AUDIO_BYTES);
        assertThat(audio.mimeType()).isEqualTo("audio/wav");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "audio/L16;codec=pcm;rate=24000    | 24000 | 1",
                "audio/l16; rate=24000; channels=1 | 24000 | 1",
                "audio/pcm;rate=16000              | 16000 | 1",
                "audio/L16;rate=48000;channels=2   | 48000 | 2",
                "audio/L16; rate=\"24000\"; channels=\"2\" | 24000 | 2"
            })
    void should_wrap_pcm_audio_in_wav_header(String mimeType, int sampleRate, int channels) throws Exception {
        returnAudio(mimeType);

        Audio audio = modelBuilder().build().synthesize("Hello").audio();

        assertThat(audio.mimeType()).isEqualTo("audio/wav");
        byte[] wav = audio.binaryData();
        ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(ascii(wav, 0)).isEqualTo("RIFF");
        assertThat(header.getInt(4)).isEqualTo(36 + AUDIO_BYTES.length);
        assertThat(ascii(wav, 8)).isEqualTo("WAVE");
        assertThat(ascii(wav, 12)).isEqualTo("fmt ");
        assertThat(header.getInt(16)).isEqualTo(16);
        assertThat(header.getShort(20)).isEqualTo((short) 1);
        assertThat(header.getShort(22)).isEqualTo((short) channels);
        assertThat(header.getInt(24)).isEqualTo(sampleRate);
        assertThat(header.getInt(28)).isEqualTo(sampleRate * channels * 2);
        assertThat(header.getShort(32)).isEqualTo((short) (channels * 2));
        assertThat(header.getShort(34)).isEqualTo((short) 16);
        assertThat(ascii(wav, 36)).isEqualTo("data");
        assertThat(header.getInt(40)).isEqualTo(AUDIO_BYTES.length);
        assertThat(Arrays.copyOfRange(wav, 44, wav.length)).isEqualTo(AUDIO_BYTES);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"audio/L16", "audio/L16;codec=pcm", "audio/l16;rate=abc", "audio/l16;rate=24000;channels=0"})
    void should_return_pcm_unchanged_when_sample_rate_or_channels_are_unknown(String mimeType) throws Exception {
        returnAudio(mimeType);

        Audio audio = modelBuilder().build().synthesize("Hello").audio();

        assertThat(audio.binaryData()).isEqualTo(AUDIO_BYTES);
        assertThat(audio.mimeType()).isEqualTo(mimeType);
    }

    @Test
    void should_send_one_part_per_speaker_turn() throws Exception {
        returnAudio("audio/wav");

        modelBuilder()
                .multiSpeakerVoiceConfig(JOE_AND_JANE)
                .build()
                .synthesize("Joe: How is it going today Jane?\nJane: Not too bad, how about you?");

        List<Part> parts = parts();
        assertThat(parts).hasSize(2);
        assertTurn(parts.get(0), "Joe", "How is it going today Jane?");
        assertTurn(parts.get(1), "Jane", "Not too bad, how about you?");
    }

    @Test
    void should_append_lines_without_speaker_to_the_current_turn() throws Exception {
        returnAudio("audio/wav");

        modelBuilder()
                .multiSpeakerVoiceConfig(JOE_AND_JANE)
                .build()
                .synthesize("Joe: first line\r\nsecond line\n\nJane: reply");

        List<Part> parts = parts();
        assertThat(parts).hasSize(2);
        assertTurn(parts.get(0), "Joe", "first line\nsecond line");
        assertTurn(parts.get(1), "Jane", "reply");
    }

    @Test
    void should_split_turns_on_windows_line_endings() throws Exception {
        returnAudio("audio/wav");

        modelBuilder()
                .multiSpeakerVoiceConfig(JOE_AND_JANE)
                .build()
                .synthesize("Joe: first line\r\nsecond line\r\nJane: reply\r\n");

        List<Part> parts = parts();
        assertThat(parts).hasSize(2);
        assertTurn(parts.get(0), "Joe", "first line\nsecond line");
        assertTurn(parts.get(1), "Jane", "reply");
    }

    @Test
    void should_reject_text_before_first_speaker() throws Exception {
        GoogleGenAiTextToSpeechModel model =
                modelBuilder().multiSpeakerVoiceConfig(JOE_AND_JANE).build();

        assertThatThrownBy(() -> model.synthesize("Intro\nJoe: Hi"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The text must start with the name of a configured speaker followed by a colon"
                        + " (configured speakers: Joe, Jane)");
        verifyNoInteractions(models);
    }

    @Test
    void should_ignore_blank_lines_before_first_speaker() throws Exception {
        returnAudio("audio/wav");

        modelBuilder().multiSpeakerVoiceConfig(JOE_AND_JANE).build().synthesize("\n   \nJoe: Hi\nJane: Hello");

        List<Part> parts = parts();
        assertThat(parts).hasSize(2);
        assertTurn(parts.get(0), "Joe", "Hi");
        assertTurn(parts.get(1), "Jane", "Hello");
    }

    @Test
    void should_match_indented_turns_and_speaker_names_literally() throws Exception {
        returnAudio("audio/wav");
        MultiSpeakerVoiceConfig speakers = MultiSpeakerVoiceConfig.builder()
                .speakerVoiceConfigs(speaker("Dr. J.R.", "Kore"), speaker("Ann (host)", "Puck"))
                .build();

        modelBuilder()
                .multiSpeakerVoiceConfig(speakers)
                .build()
                .synthesize("  Ann (host): Welcome\n  Dr. J.R.: Thanks\nDr. JXR: not a speaker");

        List<Part> parts = parts();
        assertThat(parts).hasSize(2);
        assertTurn(parts.get(0), "Ann (host)", "Welcome");
        assertTurn(parts.get(1), "Dr. J.R.", "Thanks\nDr. JXR: not a speaker");
    }

    @Test
    void should_send_multi_speaker_voice_config_as_given() throws Exception {
        returnAudio("audio/wav");

        modelBuilder().multiSpeakerVoiceConfig(JOE_AND_JANE).build().synthesize("Joe: Hi\nJane: Hello");

        GenerateContentConfig config = configCaptor.getValue();
        assertThat(config.speechConfig().get().multiSpeakerVoiceConfig()).contains(JOE_AND_JANE);
        assertThat(config.speechConfig().get().voiceConfig()).isEmpty();
    }

    @Test
    void should_build_multi_speaker_voice_config_from_speakers() throws Exception {
        returnAudio("audio/wav");

        modelBuilder().speaker("Joe", "Kore").speaker("Jane", "Puck").build().synthesize("Joe: Hi\nJane: Hello");

        assertThat(configCaptor.getValue().speechConfig().get().multiSpeakerVoiceConfig())
                .contains(JOE_AND_JANE);
    }

    @Test
    void should_add_speaker_to_configured_multi_speaker_voice_config() throws Exception {
        returnAudio("audio/wav");

        modelBuilder()
                .multiSpeakerVoiceConfig(MultiSpeakerVoiceConfig.builder()
                        .speakerVoiceConfigs(speaker("Joe", "Kore"))
                        .build())
                .speaker("Jane", "Puck")
                .build()
                .synthesize("Joe: Hi\nJane: Hello");

        assertThat(configCaptor.getValue().speechConfig().get().multiSpeakerVoiceConfig())
                .contains(JOE_AND_JANE);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"'' | Kore | speaker cannot be null or blank", "Joe | ' ' | voice cannot be null or blank"})
    void should_require_speaker_and_voice(String speaker, String voice, String message) {
        GoogleGenAiTextToSpeechModel.Builder builder = GoogleGenAiTextToSpeechModel.builder();

        assertThatThrownBy(() -> builder.speaker(speaker, voice))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }

    @Test
    void should_reject_text_without_speech_turns() throws Exception {
        GoogleGenAiTextToSpeechModel model =
                modelBuilder().multiSpeakerVoiceConfig(JOE_AND_JANE).build();

        assertThatThrownBy(() -> model.synthesize("Joe:\nJane:"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The text does not contain any speech turns to synthesize");
    }

    @Test
    void should_keep_text_with_speaker_names_in_one_part_without_multi_speaker_config() throws Exception {
        returnAudio("audio/wav");

        modelBuilder().build().synthesize("Joe: Hi\nJane: Hello");

        List<Part> parts = parts();
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).text()).contains("Joe: Hi\nJane: Hello");
        assertThat(parts.get(0).speechMetadata()).isEmpty();
    }

    @Test
    void should_throw_content_filtered_exception_when_generation_is_blocked() throws Exception {
        returnResponse(GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .finishReason(FinishReason.Known.SAFETY)
                        .build()))
                .build());
        GoogleGenAiTextToSpeechModel model = modelBuilder().build();

        assertThatThrownBy(() -> model.synthesize("Hello"))
                .isExactlyInstanceOf(ContentFilteredException.class)
                .hasMessage("The response contains no audio, finish reason: SAFETY");
    }

    @Test
    void should_throw_content_filtered_exception_when_text_is_blocked() throws Exception {
        returnResponse(GenerateContentResponse.builder()
                .promptFeedback(GenerateContentResponsePromptFeedback.builder()
                        .blockReason(BlockedReason.Known.PROHIBITED_CONTENT)
                        .build())
                .build());
        GoogleGenAiTextToSpeechModel model = modelBuilder().build();

        assertThatThrownBy(() -> model.synthesize("Hello"))
                .isExactlyInstanceOf(ContentFilteredException.class)
                .hasMessage("The text was blocked: PROHIBITED_CONTENT");
    }

    @Test
    void should_throw_when_response_contains_no_audio() throws Exception {
        returnResponse(GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(
                                        Part.builder()
                                                .text("Thinking about it")
                                                .thought(true)
                                                .build(),
                                        Part.fromText("I cannot speak")))
                                .build())
                        .finishReason(FinishReason.Known.STOP)
                        .build()))
                .build());
        GoogleGenAiTextToSpeechModel model = modelBuilder().build();

        assertThatThrownBy(() -> model.synthesize("Hello"))
                .isExactlyInstanceOf(LangChain4jException.class)
                .hasMessage("The response contains no audio, finish reason: STOP, text: I cannot speak");
    }

    @Test
    void should_map_client_exception() throws Exception {
        when(models.generateContent(any(String.class), anyList(), any(GenerateContentConfig.class)))
                .thenThrow(new ClientException(
                        400, "INVALID_ARGUMENT", "No matching speaker voice found for name: NoSuchVoice"));
        GoogleGenAiTextToSpeechModel model =
                modelBuilder().voice("NoSuchVoice").maxRetries(0).build();

        assertThatThrownBy(() -> model.synthesize("Hello")).isExactlyInstanceOf(InvalidRequestException.class);
    }

    @Test
    void should_require_model_name() {
        assertThatThrownBy(() -> GoogleGenAiTextToSpeechModel.builder()
                        .client(mock(Client.class))
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("modelName cannot be null or blank");
    }

    @Test
    void should_reject_voice_combined_with_multi_speaker_voice_config() {
        GoogleGenAiTextToSpeechModel.Builder builder = GoogleGenAiTextToSpeechModel.builder()
                .client(mock(Client.class))
                .modelName(MODEL_NAME)
                .voice("Kore")
                .multiSpeakerVoiceConfig(JOE_AND_JANE);

        assertThatThrownBy(builder::build)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "A voice cannot be combined with a multi-speaker configuration, set the voice of each speaker instead");
    }

    @Test
    void should_reject_request_voice_combined_with_multi_speaker_voice_config() throws Exception {
        GoogleGenAiTextToSpeechModel model =
                modelBuilder().multiSpeakerVoiceConfig(JOE_AND_JANE).build();
        TextToSpeechRequest request =
                TextToSpeechRequest.builder().text("Joe: Hi").voice("Puck").build();

        assertThatThrownBy(() -> model.synthesize(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "A voice cannot be combined with a multi-speaker configuration, set the voice of each speaker instead");
    }

    @Test
    void should_require_request() throws Exception {
        GoogleGenAiTextToSpeechModel model = modelBuilder().build();

        assertThatThrownBy(() -> model.synthesize((TextToSpeechRequest) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("request cannot be null");
    }

    @Test
    void should_return_google_genai_provider() throws Exception {
        assertThat(modelBuilder().build().provider()).isEqualTo(ModelProvider.GOOGLE_GENAI);
    }

    private GoogleGenAiTextToSpeechModel.Builder modelBuilder() throws Exception {
        Client client = mock(Client.class);
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);
        return GoogleGenAiTextToSpeechModel.builder().client(client).modelName(MODEL_NAME);
    }

    private void returnAudio(String mimeType) {
        returnResponse(GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(Part.fromBytes(AUDIO_BYTES, mimeType)))
                                .build())
                        .finishReason(FinishReason.Known.STOP)
                        .build()))
                .build());
    }

    private void returnResponse(GenerateContentResponse response) {
        when(models.generateContent(eq(MODEL_NAME), contentsCaptor.capture(), configCaptor.capture()))
                .thenReturn(response);
    }

    private List<Part> parts() {
        List<Content> contents = contentsCaptor.getValue();
        assertThat(contents).hasSize(1);
        return contents.get(0).parts().orElseThrow();
    }

    private static void assertTurn(Part part, String speaker, String text) {
        assertThat(part.text()).contains(text);
        assertThat(part.speechMetadata().flatMap(SpeechMetadata::speaker)).contains(speaker);
    }

    private static String voiceName(GenerateContentConfig config) {
        return config.speechConfig()
                .flatMap(speechConfig -> speechConfig.voiceConfig())
                .flatMap(VoiceConfig::prebuiltVoiceConfig)
                .flatMap(PrebuiltVoiceConfig::voiceName)
                .orElse(null);
    }

    private static SpeakerVoiceConfig speaker(String speaker, String voice) {
        return SpeakerVoiceConfig.builder()
                .speaker(speaker)
                .voiceConfig(VoiceConfig.builder()
                        .prebuiltVoiceConfig(PrebuiltVoiceConfig.builder().voiceName(voice)))
                .build();
    }

    private static String ascii(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, US_ASCII);
    }
}
