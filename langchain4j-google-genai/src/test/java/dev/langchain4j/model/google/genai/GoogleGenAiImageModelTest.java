package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import dev.langchain4j.data.image.Image;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoogleGenAiImageModelTest {

    private static final String MODEL_NAME = "gemini-3.1-flash-image";
    private static final byte[] IMAGE_BYTES = {1, 2, 3, 4};

    private final Models models = mock(Models.class);
    private final ArgumentCaptor<List<Content>> contentsCaptor = ArgumentCaptor.captor();

    @Test
    void should_send_prompt_with_user_role() throws Exception {
        when(models.generateContent(eq(MODEL_NAME), contentsCaptor.capture(), any()))
                .thenReturn(GenerateContentResponse.builder()
                        .candidates(List.of(Candidate.builder()
                                .content(Content.builder()
                                        .role("model")
                                        .parts(List.of(Part.fromBytes(IMAGE_BYTES, "image/png")))
                                        .build())
                                .build()))
                        .build());
        Image image = Image.builder()
                .base64Data(Base64.getEncoder().encodeToString(IMAGE_BYTES))
                .mimeType("image/png")
                .build();
        GoogleGenAiImageModel model = modelBuilder().build();

        model.generate("A red circle");
        model.edit(image, "Make it blue");
        model.edit(image, image, "Make it green");

        assertThat(contentsCaptor.getAllValues())
                .hasSize(3)
                .allSatisfy(contents -> assertThat(contents.get(0).role()).contains("user"));
    }

    private GoogleGenAiImageModel.Builder modelBuilder() throws Exception {
        Client client = mock(Client.class);
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);
        return GoogleGenAiImageModel.builder().client(client).modelName(MODEL_NAME);
    }
}
