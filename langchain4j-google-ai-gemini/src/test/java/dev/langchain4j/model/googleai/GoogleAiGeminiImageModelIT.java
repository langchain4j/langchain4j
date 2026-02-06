package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiImageModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String MODEL_NAME = "gemini-2.5-flash-image";
    private static final String NANO_BANANA_PRO = "gemini-3-pro-image-preview";
    private static final Path OUTPUT_DIR = Paths.get("target", "test-images");

    @BeforeAll
    static void setUp() throws IOException {
        Files.createDirectories(OUTPUT_DIR);
    }

    @Test
    void should_generate_single_image() throws IOException {
        // given
        var subject = GoogleAiGeminiImageModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .logRequests(true)
                .aspectRatio("1:1")
                .build();

        // when
        var response = subject.generate("Image of A simple red circle on a white background");

        // then
        var image = response.content();
        assertThat(image).isNotNull();
        assertThat(image.base64Data()).isNotBlank();
        assertThat(image.mimeType()).startsWith("image/");

        saveImage(image, "should_generate_single_image");
    }

    @Test
    void should_edit_image() throws IOException {
        // given
        var subject = GoogleAiGeminiImageModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .logRequestsAndResponses(true)
                .build();

        // First generate an image to edit
        var originalResponse = subject.generate("A simple blue square on a white background");
        Image originalImage = originalResponse.content();
        saveImage(originalImage, "should_edit_image_original");

        // when - edit the generated image
        var editedResponse = subject.edit(originalImage, "Change the blue square to a green triangle");

        // then
        assertThat(editedResponse).isNotNull();
        assertThat(editedResponse.content()).isNotNull();
        assertThat(editedResponse.content().base64Data()).isNotBlank();
        assertThat(editedResponse.content().mimeType()).startsWith("image/");
        assertThat(editedResponse.content().base64Data()).isNotEqualTo(originalImage.base64Data());

        saveImage(editedResponse.content(), "should_edit_image_result");
    }

    @Test
    void should_ground_image_in_search() throws IOException {
        // given
        var subject = GoogleAiGeminiImageModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(NANO_BANANA_PRO)
                .logRequestsAndResponses(true)
                .useGoogleSearchGrounding(true)
                .aspectRatio("1:1")
                .build();

        // when
        var imageResponse = subject.generate(
                """
                A kawaii illustration of the current weather forecast for Paris (France)
                showing the current temperature (in Celsius)
                """);
        saveImage(imageResponse.content(), "paris_weather_illustration");

        // then
        assertThat(imageResponse).isNotNull();
        assertThat(imageResponse.content()).isNotNull();
        assertThat(imageResponse.content().base64Data()).isNotBlank();
        assertThat(imageResponse.content().mimeType()).startsWith("image/");

        assertThat(imageResponse.metadata().get("groundingMetadata")).isNotNull();
        Map<String, Object> groundingMetadata =
                (Map<String, Object>) imageResponse.metadata().get("groundingMetadata");
        assertThat(groundingMetadata).isNotNull();

        assertThat(groundingMetadata).containsKey("webSearchQueries");
        List<String> webSearchQueries = (List<String>) groundingMetadata.get("webSearchQueries");
        assertThat(webSearchQueries).isNotEmpty();

        assertThat(groundingMetadata).containsKey("groundingChunks");
        List<Map<String, Object>> groundingChunks =
                (List<Map<String, Object>>) groundingMetadata.get("groundingChunks");
        assertThat(groundingChunks).isNotEmpty();

        groundingChunks.forEach(chunk -> {
            assertThat(chunk).containsKey("web");
            Map<String, Object> web = (Map<String, Object>) chunk.get("web");
            assertThat(web).containsKeys("uri", "title");
        });

        assertThat(groundingMetadata).containsKey("searchEntryPoint");
        Map<String, Object> searchEntryPoint = (Map<String, Object>) groundingMetadata.get("searchEntryPoint");
        assertThat(searchEntryPoint).containsKey("renderedContent");
        assertThat((String) searchEntryPoint.get("renderedContent")).isNotBlank();

        saveImage(imageResponse.content(), "paris_weather_illustration");
    }

    private static void saveImage(Image image, String fileName) throws IOException {
        String extension = getExtension(image.mimeType());
        Path filePath = OUTPUT_DIR.resolve(fileName + "." + extension);

        byte[] imageBytes = Base64.getDecoder().decode(image.base64Data());
        Files.write(filePath, imageBytes);

        System.out.println("Saved image to: " + filePath.toAbsolutePath());
    }

    private static String getExtension(String mimeType) {
        if (mimeType == null) {
            return "png";
        }
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }
}
