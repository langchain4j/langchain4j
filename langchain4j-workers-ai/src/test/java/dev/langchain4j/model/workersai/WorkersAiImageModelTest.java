package dev.langchain4j.model.workersai;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.internal.Json;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
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
    void should_send_the_pixels_of_the_image_and_the_mask_given_as_base64_data() throws Exception {
        WorkersAiImageModel model = model();

        model.edit(base64Image(png(RED, RED)), base64Image(png(BLUE, BLUE)), "a cat");

        assertThat(sentPixels()).containsExactlyInAnyOrder(List.of(RED, RED), List.of(BLUE, BLUE));
    }

    @Test
    void should_send_the_pixels_of_an_image_given_as_base64_data() throws Exception {
        WorkersAiImageModel model = model();

        model.edit(base64Image(png(RED, BLUE)), "a cat");

        assertThat(sentPixels()).containsExactly(List.of(RED, BLUE));
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
    void should_reject_a_mask_given_by_url() throws Exception {
        WorkersAiImageModel model = model();

        assertThatThrownBy(() -> model.edit(
                        base64Image(png(RED, RED)), Image.builder().url("https://example.com/mask.png").build(), "a cat"))
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

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg", "gif", "bmp"})
    void should_decode_images_of_the_supported_formats(String format) throws Exception {
        WorkersAiImageModel model = model();

        model.edit(base64Image(image(format)), "a cat");

        assertThat(sentPixels()).singleElement().asList().hasSize(4);
    }

    @Test
    void should_not_decode_images_of_other_formats_even_if_an_image_reader_is_installed() throws Exception {
        byte[] image = image("tiff");
        assertThat(ImageIO.read(new ByteArrayInputStream(image)))
                .as("an image reader for TIFF is installed")
                .isNotNull();
        WorkersAiImageModel model = model();

        assertThatThrownBy(() -> model.edit(base64Image(image), "a cat"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("The image format is not supported: only PNG, JPEG, GIF, BMP and WebP images can be edited");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_an_image_with_too_many_pixels_before_decoding_it() throws Exception {
        WorkersAiImageModel model = model();

        assertThatThrownBy(() -> model.edit(base64Image(pngHeaderOnly(20_000, 20_000)), "a cat"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("The image has 20000 x 20000 pixels, more than LangChain4j decodes for Workers AI"
                        + " (4194304 pixels, 2048 x 2048)");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_accept_an_image_with_the_maximum_number_of_pixels() throws Exception {
        WorkersAiImageModel model = model();

        model.edit(base64Image(png(new int[2048 * 2048], 2048)), "a cat");

        assertThat(httpClient.requests()).hasSize(1);
    }

    @Test
    void should_reject_content_that_is_not_an_image() {
        WorkersAiImageModel model = model();
        Image text = Image.builder()
                .base64Data(Base64.getEncoder().encodeToString("hello".getBytes(US_ASCII)))
                .mimeType("image/png")
                .build();

        assertThatThrownBy(() -> model.edit(text, "a cat"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not supported");
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
    private List<List<Integer>> sentPixels() {
        Map<String, Object> body = Json.fromJson(httpClient.request().body(), Map.class);
        List<List<Integer>> pixels = new ArrayList<>();
        for (String field : List.of("image", "mask")) {
            if (body.get(field) != null) {
                pixels.add((List<Integer>) body.get(field));
            }
        }
        return pixels;
    }

    private static Image base64Image(byte[] bytes) {
        return Image.builder()
                .base64Data(Base64.getEncoder().encodeToString(bytes))
                .mimeType("image/png")
                .build();
    }

    private static byte[] image(String format) throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, bytes)).as("an image writer for %s is installed", format).isTrue();
        return bytes.toByteArray();
    }

    private static byte[] png(int... argbPixels) throws IOException {
        return png(argbPixels, argbPixels.length);
    }

    private static byte[] png(int[] argbPixels, int width) throws IOException {
        BufferedImage image = new BufferedImage(width, argbPixels.length / width, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, argbPixels.length / width, argbPixels, 0, width);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        return png.toByteArray();
    }

    /**
     * A PNG file of a few bytes whose header declares the given size: decoding it would need memory for every pixel.
     */
    private static byte[] pngHeaderOnly(int width, int height) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(png);
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream headerOut = new DataOutputStream(header);
        headerOut.writeInt(width);
        headerOut.writeInt(height);
        headerOut.write(new byte[] {8, 6, 0, 0, 0}); // 8 bits per channel, RGBA
        writeChunk(out, "IHDR", header.toByteArray());
        writeChunk(out, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void writeChunk(DataOutputStream out, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeInt(data.length);
        out.write(typeBytes);
        out.write(data);
        out.writeInt((int) crc.getValue());
    }
}
