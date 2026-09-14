package dev.langchain4j.model.workersai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkersAiImageModelTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    @TempDir
    Path tempDir;

    private MockHttpClient mockHttpClient;
    private WorkersAiImageModel model;
    private Image sourceImage;
    private Image maskImage;

    @BeforeEach
    void setUp() throws IOException {
        mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new byte[] {1, 2, 3})
                .build());
        model = WorkersAiImageModel.builder()
                .accountId("account-id")
                .apiToken("api-token")
                .modelName(WorkersAiImageModelName.STABLE_DIFFUSION_V1_5_IMG2IMG.toString())
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .build();
        sourceImage = pngImage("source.png", RED);
        maskImage = pngImage("mask.png", BLUE);
    }

    /**
     * Precondition for the two routing tests below: the expected values they compare against are
     * produced by {@link WorkersAiImageModel#getPixels}, so a swap between the {@code image} and
     * {@code mask} fields is only observable while the two fixtures convert to different arrays.
     */
    @Test
    void source_and_mask_fixtures_convert_to_different_pixel_arrays() throws Exception {
        assertThat(pixelsOf(sourceImage)).isNotEqualTo(pixelsOf(maskImage));
    }

    @Test
    void edit_sends_the_source_image_in_the_image_field() throws Exception {
        model.edit(sourceImage, "make it blue");

        JsonNode body = requestBody();
        assertThat(pixelArray(body.get("image"))).isEqualTo(pixelsOf(sourceImage));
        // The key is present with an explicit null value: WorkersAiJsonUtils intentionally uses a
        // plain ObjectMapper with default configuration, which serializes nulls rather than omitting them.
        assertThat(body.has("mask")).isTrue();
        assertThat(body.get("mask").isNull()).isTrue();
    }

    @Test
    void edit_with_a_mask_does_not_swap_the_image_and_mask_fields() throws Exception {
        model.edit(sourceImage, maskImage, "make it blue");

        JsonNode body = requestBody();
        assertThat(pixelArray(body.get("image"))).isEqualTo(pixelsOf(sourceImage));
        assertThat(pixelArray(body.get("mask"))).isEqualTo(pixelsOf(maskImage));
    }

    private Image pngImage(String fileName, int rgb) throws IOException {
        BufferedImage bufferedImage = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < bufferedImage.getHeight(); y++) {
            for (int x = 0; x < bufferedImage.getWidth(); x++) {
                bufferedImage.setRGB(x, y, rgb);
            }
        }
        File file = tempDir.resolve(fileName).toFile();
        ImageIO.write(bufferedImage, "png", file);
        return Image.builder().url(file.toURI()).build();
    }

    private int[] pixelsOf(Image image) throws Exception {
        return model.getPixels(image.url().toURL());
    }

    private JsonNode requestBody() throws IOException {
        return OBJECT_MAPPER.readTree(mockHttpClient.request().body());
    }

    private static int[] pixelArray(JsonNode node) {
        assertThat(node.isArray())
                .as("expected a JSON array, but was: %s", node)
                .isTrue();
        int[] pixels = new int[node.size()];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = node.get(i).intValue();
        }
        return pixels;
    }
}
