package dev.langchain4j.model.openai;

import static dev.langchain4j.model.openai.OpenAiImageModelName.GPT_IMAGE_1;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled("Run manually before release. Expensive to run very often.")
class OpenAiImageModelIT {

    Logger log = LoggerFactory.getLogger(OpenAiImageModelIT.class);

    OpenAiImageModel.OpenAiImageModelBuilder modelBuilder = OpenAiImageModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_IMAGE_1)
            .logRequests(true)
            .logResponses(true);

    @Test
    void simple_image_generation_works() {
        OpenAiImageModel model = modelBuilder.build();

        Response<Image> response = model.generate("Beautiful house on country side");

        Image image = response.content();
        assertThat(image.base64Data()).isNotNull().isNotBlank();
        assertThat(image.mimeType()).isNotNull();
    }

    @Test
    void multiple_images_generation_works() {
        OpenAiImageModel model = modelBuilder.outputFormat("png").build();

        Response<List<Image>> response = model.generate("Cute red parrot sings", 2);

        assertThat(response.content()).hasSize(2);

        Image image1 = response.content().get(0);
        assertThat(image1.base64Data()).isNotNull().isBase64();
        assertThat(image1.mimeType()).isEqualTo("image/png");

        Image image2 = response.content().get(1);
        assertThat(image2.base64Data()).isNotNull().isBase64();
        assertThat(image2.mimeType()).isEqualTo("image/png");
    }

    @Test
    void image_generation_with_quality_works() {
        OpenAiImageModel model = OpenAiImageModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_IMAGE_1)
                .quality("high")
                .logRequests(true)
                .logResponses(true)
                .build();

        Response<Image> response =
                model.generate("Beautiful house on country side, cowboy plays guitar, dog sitting at the door");

        Image image = response.content();
        assertThat(image.base64Data()).isNotNull().isNotBlank();
    }

    @Test
    void image_generation_with_transparent_background_works() {
        OpenAiImageModel model = OpenAiImageModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_IMAGE_1)
                .background("transparent")
                .outputFormat("png")
                .logRequests(true)
                .logResponses(true)
                .build();

        Response<Image> response = model.generate("A red apple on a white background");

        Image image = response.content();
        assertThat(image.base64Data()).isNotNull().isNotBlank();
        assertThat(image.mimeType()).isEqualTo("image/png");
    }

    @Test
    void image_edit_works() {
        OpenAiImageModel model = modelBuilder.build();

        Image original = model.generate("A red apple on a white background").content();

        Response<Image> response = model.edit(original, "Make the apple green");

        Image edited = response.content();
        assertThat(edited.base64Data()).isNotNull().isNotBlank();
        saveToDisk(edited, "edited-apple");
    }

    @Test
    void image_edit_with_mask_works() {
        OpenAiImageModel model = modelBuilder.outputFormat("png").build();

        Image original = model.generate("A red apple on a white background").content();
        Image mask = transparentTopHalfMask(original);

        Response<Image> response = model.edit(original, mask, "Make the apple green");

        Image edited = response.content();
        assertThat(edited.base64Data()).isNotNull().isNotBlank();
        assertThat(edited.mimeType()).isEqualTo("image/png");
        saveToDisk(edited, "edited-apple-with-mask");
    }

    /**
     * Builds a valid edit mask for the given image: a PNG with an alpha channel, of the same dimensions
     * as the source image, where the top half is fully transparent (the area to be edited) and the bottom
     * half is fully opaque (the area to be preserved).
     */
    private Image transparentTopHalfMask(Image source) {
        try {
            BufferedImage sourceImage =
                    ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(source.base64Data())));
            int width = sourceImage.getWidth();
            int height = sourceImage.getHeight();

            BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                int argb = y < height / 2 ? 0x00000000 : 0xFF000000; // transparent top, opaque bottom
                for (int x = 0; x < width; x++) {
                    mask.setRGB(x, y, argb);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(mask, "png", out);
            return Image.builder()
                    .base64Data(Base64.getEncoder().encodeToString(out.toByteArray()))
                    .mimeType("image/png")
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveToDisk(Image image, String fileNamePrefix) {
        String extension = image.mimeType() != null ? image.mimeType().replace("image/", "") : "png";
        try {
            Path path = Files.createTempFile(fileNamePrefix + "-", "." + extension);
            Files.write(path, Base64.getDecoder().decode(image.base64Data()));
            log.info("Image saved to: {}", path.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void should_use_enum_as_model_name() {

        // given
        OpenAiImageModel model = OpenAiImageModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_IMAGE_1)
                .logRequests(true)
                .logResponses(true)
                .build();

        String prompt = "Beautiful house on country side";

        // when
        Response<Image> response = model.generate(prompt);

        // then
        Image image = response.content();
        assertThat(image.base64Data()).isNotNull().isNotBlank();
    }
}
