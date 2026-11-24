package dev.langchain4j.model.workersai;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.media.FetchedMedia;
import dev.langchain4j.data.media.MediaFetcher;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.internal.Json;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkersAiImageModelTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    private final MockHttpClient httpClient = new MockHttpClient(
            SuccessfulHttpResponse.builder().statusCode(200).body(new byte[] {1}).build());

    @Test
    void should_send_the_pixels_of_the_image_and_the_mask_given_as_base64_data() throws Exception {
        WorkersAiImageModel model = model(request -> {
            throw new AssertionError("must not download");
        });

        model.edit(base64Image(png(RED, RED)), base64Image(png(BLUE, BLUE)), "a cat");

        assertThat(sentPixels()).containsExactlyInAnyOrder(List.of(RED, RED), List.of(BLUE, BLUE));
    }

    @Test
    void should_send_the_same_request_for_an_image_given_as_base64_data_and_by_url() throws Exception {
        byte[] png = png(RED, BLUE);
        model(null).edit(base64Image(png), "a cat");
        model(request -> FetchedMedia.from(png, "image/png"))
                .edit(Image.builder().url("https://example.com/image.png").build(), "a cat");

        assertThat(httpClient.requests()).hasSize(2);
        assertThat(httpClient.requests().get(0).body())
                .contains("[" + RED + "," + BLUE + "]")
                .isEqualTo(httpClient.requests().get(1).body());
    }

    @Test
    void should_not_decode_base64_images_of_other_formats() throws Exception {
        WorkersAiImageModel model = model(null);

        assertThatThrownBy(() -> model.edit(base64Image(image("tiff")), "a cat"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("The image format is not supported: only PNG, JPEG, GIF, BMP and WebP images can be edited");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_a_base64_image_with_too_many_pixels_before_decoding_it() throws Exception {
        WorkersAiImageModel model = model(null);

        assertThatThrownBy(() -> model.edit(base64Image(pngHeaderOnly(20_000, 20_000)), "a cat"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("20000 x 20000");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_an_image_with_neither_base64_data_nor_a_url() {
        WorkersAiImageModel model = model(null);

        assertThatThrownBy(() -> model.edit(Image.builder().build(), "a cat"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The image has neither base64 data nor a URL");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_send_the_pixels_of_the_image_and_the_mask_given_by_url() throws Exception {
        URI imageUrl = URI.create("https://example.com/image.png");
        URI maskUrl = URI.create("https://example.com/mask.png");
        Map<URI, byte[]> images = Map.of(imageUrl, png(RED, RED), maskUrl, png(BLUE, BLUE));
        WorkersAiImageModel model = model(request -> FetchedMedia.from(images.get(request.url()), "image/png"));

        model.edit(Image.builder().url(imageUrl).build(), Image.builder().url(maskUrl).build(), "a cat");

        assertThat(sentPixels()).containsExactlyInAnyOrder(List.of(RED, RED), List.of(BLUE, BLUE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg", "gif", "bmp"})
    void should_decode_images_of_the_supported_formats(String format) throws Exception {
        byte[] image = image(format);
        WorkersAiImageModel model = model(request -> FetchedMedia.from(image));

        model.edit(Image.builder().url("https://example.com/image").build(), "a cat");

        assertThat(sentPixels()).singleElement().asList().hasSize(4);
    }

    @Test
    void should_not_decode_images_of_other_formats_even_if_an_image_reader_is_installed() throws Exception {
        byte[] image = image("tiff");
        assertThat(ImageIO.read(new ByteArrayInputStream(image)))
                .as("an image reader for TIFF is installed")
                .isNotNull();
        WorkersAiImageModel model = model(request -> FetchedMedia.from(image));

        assertThatThrownBy(() -> model.edit(Image.builder().url("https://example.com/image").build(), "a cat"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("The image format is not supported: only PNG, JPEG, GIF, BMP and WebP images can be edited");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_download_images_given_by_url_through_the_configured_media_fetcher() throws Exception {
        URI url = URI.create("https://example.com/source.png");
        byte[] png = png(RED, BLUE);
        List<URI> fetched = new ArrayList<>();
        WorkersAiImageModel model = model(request -> {
            fetched.add(request.url());
            return FetchedMedia.from(png, "image/png");
        });

        model.edit(Image.builder().url(url).build(), "a cat");

        assertThat(fetched).containsExactly(url);
        assertThat(sentPixels()).containsExactly(List.of(RED, BLUE));
    }

    @Test
    void getPixels_should_download_through_the_configured_media_fetcher() throws Exception {
        byte[] png = png(BLUE, RED);
        List<URI> fetched = new ArrayList<>();
        WorkersAiImageModel model = model(request -> {
            fetched.add(request.url());
            return FetchedMedia.from(png, "image/png");
        });

        int[] pixels = model.getPixels(new URL("https://example.com/source.png"));

        assertThat(pixels).containsExactly(BLUE, RED);
        assertThat(fetched).containsExactly(URI.create("https://example.com/source.png"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "/home/me/cat.png"})
    void should_not_read_local_files_by_default(String localFile) {
        WorkersAiImageModel model = model(null);

        assertThatThrownBy(() -> model.edit(Image.builder().url(localFile).build(), "a cat"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("from(Path");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_reject_image_urls_of_non_public_hosts_by_default() {
        WorkersAiImageModel model = model(null);

        assertThatThrownBy(() -> model.edit(Image.builder().url("http://127.0.0.1/cat.png").build(), "a cat"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("non-public address");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void getPixels_should_not_read_local_files_by_default() {
        WorkersAiImageModel model = model(null);

        assertThatThrownBy(() -> model.getPixels(new URL("file:///etc/passwd")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("from(Path");
    }

    @Test
    void should_reject_an_image_with_too_many_pixels_before_decoding_it() throws Exception {
        byte[] png = pngHeaderOnly(20_000, 20_000);
        WorkersAiImageModel model = model(request -> FetchedMedia.from(png, "image/png"));

        assertThatThrownBy(() -> model.edit(Image.builder().url("https://example.com/huge.png").build(), "a cat"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("The image has 20000 x 20000 pixels, more than LangChain4j decodes for Workers AI"
                        + " (4194304 pixels, 2048 x 2048)");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void should_accept_an_image_with_the_maximum_number_of_pixels() throws Exception {
        byte[] png = png(new int[2048 * 2048], 2048);
        WorkersAiImageModel model = model(request -> FetchedMedia.from(png, "image/png"));

        model.edit(Image.builder().url("https://example.com/large.png").build(), "a cat");

        assertThat(httpClient.requests()).hasSize(1);
    }

    @Test
    void should_reject_content_that_is_not_an_image() {
        WorkersAiImageModel model = model(request -> FetchedMedia.from("hello".getBytes(US_ASCII), "text/plain"));

        assertThatThrownBy(() -> model.edit(Image.builder().url("https://example.com/hello.txt").build(), "a cat"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not supported");
        assertThat(httpClient.requests()).isEmpty();
    }

    private WorkersAiImageModel model(MediaFetcher mediaFetcher) {
        return WorkersAiImageModel.builder()
                .accountId("account")
                .apiToken("token")
                .modelName(WorkersAiImageModelName.STABLE_DIFFUSION_V1_5_IN_PAINTING.toString())
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .mediaFetcher(mediaFetcher)
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
