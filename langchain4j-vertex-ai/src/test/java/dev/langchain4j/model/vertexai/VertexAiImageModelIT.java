package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class VertexAiImageModelIT {

    private static final String ENDPOINT = System.getenv("GCP_VERTEXAI_ENDPOINT");
    private static final String LOCATION = System.getenv("GCP_LOCATION");
    private static final String PROJECT = System.getenv("GCP_PROJECT_ID");
    private static final String PUBLISHER = "google";

    private static Image fromPath(Path path) {
        try {
            byte[] allBytes = Files.readAllBytes(path);
            String base64 = Base64.getEncoder().encodeToString(allBytes);
            return Image.builder()
                    .url(path.toUri())
                    .base64Data(base64)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void beforeEach() throws InterruptedException {
        Thread.sleep(10_000); // to prevent hitting rate limits
    }

    @Test
    public void should_generate_one_image_with_persistence() {
        VertexAiImageModel imagenModel = VertexAiImageModel.builder()
                .endpoint(ENDPOINT)
                .location(LOCATION)
                .project(PROJECT)
                .publisher(PUBLISHER)
                .modelName("imagegeneration@005")
                .maxRetries(2)
                .withPersisting()
                .build();

        Response<Image> imageResponse = imagenModel.generate("watercolor of a colorful parrot drinking a cup of coffee");

        // has a URL because the generated image is persisted into a file
        assertThat(imageResponse.content().url()).isNotNull();
        assertThat(new File(imageResponse.content().url())).exists();
        // checks that there's Base64 data representing the image
        assertThat(imageResponse.content().base64Data()).isNotNull();
        // checks that the Base64 content is valid Base64 encoded
        assertDoesNotThrow(() -> Base64.getDecoder().decode(imageResponse.content().base64Data()));
    }

    @Test
    public void should_generate_three_images_with_persistence() {
        VertexAiImageModel imagenModel = VertexAiImageModel.builder()
                .endpoint(ENDPOINT)
                .location(LOCATION)
                .project(PROJECT)
                .publisher(PUBLISHER)
                .modelName("imagegeneration@005")
                .withPersisting()
                .build();

        Response<List<Image>> imageListResponse = imagenModel.generate("photo of a sunset over Malibu beach", 3);

        assertThat(imageListResponse.content()).hasSize(3);
        imageListResponse.content().forEach(img -> {
            assertThat(img.url()).isNotNull();
            assertThat(img.base64Data()).isNotNull();
        });
    }

    @Test
    public void should_use_image_style_seed_image_source_and_mask_for_editing() throws URISyntaxException {
        VertexAiImageModel model = VertexAiImageModel.builder()
                .endpoint(ENDPOINT)
                .location(LOCATION)
                .project(PROJECT)
                .publisher(PUBLISHER)
                .modelName("imagegeneration@002")
                .seed(19707L)
                .sampleImageStyle(VertexAiImageModel.ImageStyle.PHOTOGRAPH)
                .guidanceScale(100)
                .maxRetries(4)
                .withPersisting()
                .build();

        Response<Image> forestResp = model.generate("lush forest");

        assertThat(forestResp.content().base64Data()).isNotNull();

        URI maskFileUri = Objects.requireNonNull(getClass().getClassLoader().getResource("mask.png")).toURI();

        Response<Image> compositeResp = model.edit(
                forestResp.content(), fromPath(Paths.get(maskFileUri)), "red trees"
        );

        assertThat(compositeResp.content().base64Data()).isNotNull();
    }

    @Test
    public void should_use_persistTo_and_image_upscaling() {
        Path defaultTempDirPath = Paths.get(System.getProperty("java.io.tmpdir"));

        VertexAiImageModel imagenModel = VertexAiImageModel.builder()
                .endpoint(ENDPOINT)
                .location(LOCATION)
                .project(PROJECT)
                .publisher(PUBLISHER)
                .modelName("imagegeneration@002")
                .sampleImageSize(1024)
                .withPersisting()
                .persistTo(defaultTempDirPath)
                .maxRetries(3)
                .build();

        Response<Image> imageResponse =
                imagenModel.generate("A black bird looking itself in an antique mirror");

        assertThat(imageResponse.content().url()).isNotNull();
        assertThat(new File(imageResponse.content().url())).exists();
        assertThat(imageResponse.content().base64Data()).isNotNull();

        VertexAiImageModel imagenModelForUpscaling = VertexAiImageModel.builder()
                .endpoint(ENDPOINT)
                .location(LOCATION)
                .project(PROJECT)
                .publisher(PUBLISHER)
                .modelName("imagegeneration@002")
                .sampleImageSize(4096)
                .withPersisting()
                .persistTo(defaultTempDirPath)
                .maxRetries(3)
                .build();

        Response<Image> upscaledImageResponse =
                imagenModelForUpscaling.edit(imageResponse.content(), "");

        assertThat(upscaledImageResponse.content().url()).isNotNull();
        assertThat(new File(upscaledImageResponse.content().url())).exists();
        assertThat(upscaledImageResponse.content().base64Data()).isNotNull();
    }

    @Test
    public void should_use_negative_prompt_and_different_prompt_language() {
        VertexAiImageModel imagenModel = VertexAiImageModel.builder()
                .endpoint(ENDPOINT)
                .location(LOCATION)
                .project(PROJECT)
                .publisher(PUBLISHER)
                .modelName("imagegeneration@005")
                .language("ja")
                .negativePrompt("pepperoni, pineapple")
                .maxRetries(2)
                .withPersisting()
                .build();

        Response<Image> imageResponse = imagenModel.generate("ピザ"); // pizza

        assertThat(imageResponse.content().url()).isNotNull();
        assertThat(imageResponse.content().base64Data()).isNotNull();
    }

    @Test
    public void should_raise_error_on_problematic_prompt_or_content_generation() {
        VertexAiImageModel imagenModel = VertexAiImageModel.builder()
                .endpoint(ENDPOINT)
                .location(LOCATION)
                .project(PROJECT)
                .publisher(PUBLISHER)
                .modelName("imagegeneration@005")
                .withPersisting()
                .build();

        assertThatExceptionOfType(Throwable.class).isThrownBy(() -> imagenModel.generate("a nude woman"));
    }

    //    @Test
    public void should_generate_one_imagen_v3_with_persistence() {
//        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG");

        VertexAiImageModel imagenModel = VertexAiImageModel.builder()
                .endpoint(ENDPOINT)
                .location(LOCATION)
                .project(PROJECT)
                .publisher(PUBLISHER)
                .modelName("imagen-3.0-generate-preview-0611")
                .aspectRatio(VertexAiImageModel.AspectRatio.LANDSCAPE)
                .mimeType(VertexAiImageModel.MimeType.JPEG)
                .compressionQuality(80)
//            .personGeneration(VertexAiImageModel.PersonGeneration.dont_allow) // TODO: extra test
                .watermark(false) // true by default with v3 // TODO: not sure how to test a watermark is present or not
                .maxRetries(2)
                .withPersisting()
                .logRequests(true)
                .logResponses(true)
                .build();

        String prompt = "A cubist oil painting close-up, with heavy brush strokes full of paint, of an imaginary three mast boat flying in the clouds";

        Response<Image> imageResponse = imagenModel.generate(prompt);

        // has a URL because the generated image is persisted into a file
        assertThat(imageResponse.content().url()).isNotNull();
        assertThat(new File(imageResponse.content().url())).exists();
        // checks that there's Base64 data representing the image
        assertThat(imageResponse.content().base64Data()).isNotNull();
        // checks that the Base64 content is valid Base64 encoded
        assertDoesNotThrow(() -> Base64.getDecoder().decode(imageResponse.content().base64Data()));
    }
}
