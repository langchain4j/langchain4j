package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class GoogleGenAiTextToSpeechModelLocaleTest {

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ", "lt-LT", "en-US"})
    void uppercase_pcm_mime_type_should_be_wrapped_locale_independently(String languageTag) throws Exception {
        Models models = mock(Models.class);
        when(models.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
                .thenReturn(GenerateContentResponse.builder()
                        .candidates(List.of(Candidate.builder()
                                .content(Content.builder()
                                        .role("model")
                                        .parts(List.of(Part.fromBytes(new byte[] {1, 2}, "AUDIO/L16;RATE=24000")))
                                        .build())
                                .build()))
                        .build());
        Client client = mock(Client.class);
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);
        GoogleGenAiTextToSpeechModel model = GoogleGenAiTextToSpeechModel.builder()
                .client(client)
                .modelName("gemini-2.5-flash-preview-tts")
                .build();

        Locale previousDefault = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag(languageTag));
            assertThat(model.synthesize("Hello").audio().mimeType()).isEqualTo("audio/wav");
        } finally {
            Locale.setDefault(previousDefault);
        }
    }
}
