package dev.langchain4j.model.workersai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.internal.Json;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkersAiImageModelTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    private final MockHttpClient httpClient = new MockHttpClient(
            SuccessfulHttpResponse.builder().statusCode(200).body(new byte[] {1}).build());

    @Test
    void should_send_the_bytes_of_the_image_and_the_mask_given_as_base64_data() {
        WorkersAiImageModel model = model();

        model.edit(base64Image(1, 2, (byte) 255), base64Image((byte) 128, 0), "a cat");

        assertThat(sentBody()).containsEntry("image", List.of(1, 2, 255)).containsEntry("mask", List.of(128, 0));
    }

    @Test
    void should_send_the_bytes_of_an_image_given_as_base64_data() {
        WorkersAiImageModel model = model();

        model.edit(base64Image(1, (byte) 200), "a cat");

        Map<String, Object> body = sentBody();
        assertThat(body).containsEntry("image", List.of(1, 200));
        assertThat(body.get("mask")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/cat.png", "http://localhost/cat.png", "file:///etc/hosts", "cat.png"})
    void should_reject_images_given_by_url(String url) {
        WorkersAiImageModel model = model();

        assertThatThrownBy(() -> model.edit(Image.builder().url(url).build(), "a cat"))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(WorkersAiImageModel.IMAGE_URL_NOT_SUPPORTED)
                .hasMessageNotContaining(url);
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_a_mask_given_by_url() {
        WorkersAiImageModel model = model();

        assertThatThrownBy(() -> model.edit(
                        base64Image(1, 2), Image.builder().url("https://example.com/mask.png").build(), "a cat"))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(WorkersAiImageModel.IMAGE_URL_NOT_SUPPORTED);
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_an_image_with_neither_base64_data_nor_a_url() {
        WorkersAiImageModel model = model();

        assertThatThrownBy(() -> model.edit(Image.builder().build(), "a cat"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The image has neither base64 data nor a URL");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void getPixels_should_read_the_image_at_the_given_url(@TempDir Path directory) throws Exception {
        Path image = Files.write(directory.resolve("image.png"), png(BLUE, RED));

        int[] pixels = model().getPixels(image.toUri().toURL());

        assertThat(pixels).containsExactly(BLUE, RED);
    }

    private WorkersAiImageModel model() {
        return WorkersAiImageModel.builder()
                .accountId("account")
                .apiToken("token")
                .modelName(WorkersAiImageModelName.STABLE_DIFFUSION_V1_5_IN_PAINTING.toString())
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentBody() {
        return Json.fromJson(httpClient.request().body(), Map.class);
    }

    private static Image base64Image(int... bytes) {
        byte[] content = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            content[i] = (byte) bytes[i];
        }
        return Image.builder()
                .base64Data(Base64.getEncoder().encodeToString(content))
                .mimeType("image/png")
                .build();
    }

    private static byte[] png(int... argbPixels) throws IOException {
        BufferedImage image = new BufferedImage(argbPixels.length, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, argbPixels.length, 1, argbPixels, 0, argbPixels.length);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        return png.toByteArray();
    }
}
