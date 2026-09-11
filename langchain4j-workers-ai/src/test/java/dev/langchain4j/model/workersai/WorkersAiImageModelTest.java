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
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkersAiImageModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void should_send_image_in_image_field_when_editing() throws Exception {
        File sourceImage = writePng("source");
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new byte[] {1, 2, 3})
                .build());
        WorkersAiImageModel model = imageModel(mockHttpClient);

        model.edit(Image.builder().url(sourceImage.toURI()).build(), "make it blue");

        JsonNode body = objectMapper.readTree(mockHttpClient.request().body());
        assertThat(body.get("prompt").asText()).isEqualTo("make it blue");
        assertThat(body.get("image")).isNotNull();
        assertThat(body.get("image").isArray()).isTrue();
        assertThat(body.get("image").size()).isPositive();
        assertThat(body.get("mask").isNull()).isTrue();
    }

    @Test
    void should_send_image_and_mask_in_distinct_fields_when_editing_with_mask() throws Exception {
        File sourceImage = writePng("source");
        File maskImage = writePng("mask");
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new byte[] {1, 2, 3})
                .build());
        WorkersAiImageModel model = imageModel(mockHttpClient);

        model.edit(
                Image.builder().url(sourceImage.toURI()).build(),
                Image.builder().url(maskImage.toURI()).build(),
                "make it blue");

        JsonNode body = objectMapper.readTree(mockHttpClient.request().body());
        assertThat(body.get("prompt").asText()).isEqualTo("make it blue");
        assertThat(body.get("image")).isNotNull();
        assertThat(body.get("image").isArray()).isTrue();
        assertThat(body.get("mask")).isNotNull();
        assertThat(body.get("mask").isArray()).isTrue();
        assertThat(body.get("image")).isNotEqualTo(body.get("mask"));
    }

    @Test
    void should_send_no_image_nor_mask_when_generating() throws Exception {
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new byte[] {1, 2, 3})
                .build());
        WorkersAiImageModel model = imageModel(mockHttpClient);

        model.generate("a blue bird");

        JsonNode body = objectMapper.readTree(mockHttpClient.request().body());
        assertThat(body.get("prompt").asText()).isEqualTo("a blue bird");
        assertThat(body.get("image").isNull()).isTrue();
        assertThat(body.get("mask").isNull()).isTrue();
    }

    private WorkersAiImageModel imageModel(MockHttpClient mockHttpClient) {
        return WorkersAiImageModel.builder()
                .accountId("account-id")
                .apiToken("api-token")
                .modelName("test-model")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .build();
    }

    private File writePng(String name) throws Exception {
        BufferedImage bufferedImage = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        if (name.equals("mask")) {
            bufferedImage.setRGB(0, 0, 0xFFFFFF);
            bufferedImage.setRGB(1, 0, 0xFFFFFF);
            bufferedImage.setRGB(0, 1, 0x000000);
            bufferedImage.setRGB(1, 1, 0x000000);
        } else {
            bufferedImage.setRGB(0, 0, 0xFF0000);
            bufferedImage.setRGB(1, 0, 0x00FF00);
            bufferedImage.setRGB(0, 1, 0x0000FF);
            bufferedImage.setRGB(1, 1, 0x000000);
        }
        File file = tempDir.resolve(name + ".png").toFile();
        ImageIO.write(bufferedImage, "png", file);
        return file;
    }
}
