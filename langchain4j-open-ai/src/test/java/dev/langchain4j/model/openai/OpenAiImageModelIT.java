package dev.langchain4j.model.openai;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;

import static dev.langchain4j.model.openai.OpenAiImageModelName.DALL_E_2;
import static dev.langchain4j.model.openai.OpenAiImageModelName.DALL_E_3;
import static dev.langchain4j.model.openai.OpenAiImageModelName.GPT_IMAGE_2;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Run manually before release. Expensive to run very often.")
class OpenAiImageModelIT {

    Logger log = LoggerFactory.getLogger(OpenAiImageModelIT.class);

    OpenAiImageModel.OpenAiImageModelBuilder modelBuilder = OpenAiImageModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(DALL_E_2)
            .size("256x256")
            .logRequests(true)
            .logResponses(true);

    @Test
    void simple_image_generation_works() {
        OpenAiImageModel model = modelBuilder.build();

        Response<Image> response = model.generate("Beautiful house on country side");

        URI remoteImage = response.content().url();
        log.info("Your remote image is here: {}", remoteImage);
        assertThat(remoteImage).isNotNull();
    }

    @Test
    void multiple_images_generation_with_base64_works() {
        OpenAiImageModel model = modelBuilder.responseFormat("b64_json").build();

        Response<List<Image>> response = model.generate("Cute red parrot sings", 2);

        assertThat(response.content()).hasSize(2);

        Image localImage1 = response.content().get(0);
        assertThat(localImage1.base64Data()).isNotNull().isBase64();

        Image localImage2 = response.content().get(1);
        assertThat(localImage2.base64Data()).isNotNull().isBase64();
    }

    @Test
    void image_generation_with_dalle3_works() {
        OpenAiImageModel model = OpenAiImageModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(DALL_E_3)
                .quality("hd")
                .logRequests(true)
                .logResponses(true)
                .build();

        Response<Image> response = model.generate(
                "Beautiful house on country side, cowboy plays guitar, dog sitting at the door"
        );

        URI remoteImage = response.content().url();
        log.info("Your remote image is here: {}", remoteImage);
        assertThat(remoteImage).isNotNull();

        String revisedPrompt = response.content().revisedPrompt();
        log.info("Your revised prompt: {}", revisedPrompt);
        assertThat(revisedPrompt).hasSizeGreaterThan(50);
    }

    @Test
    void dall_e_2_edit_works() {
        OpenAiImageModel model = OpenAiImageModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(DALL_E_2)
                .size("1024x1024")
                .responseFormat("b64_json")
                .logRequests(true)
                .logResponses(true)
                .build();

        Image source = solidColorPng(1024, 1024, new Color(180, 220, 255));

        Response<Image> response = model.edit(source, "Add a smiling cartoon sun in the upper-right corner");

        assertThat(response.content().base64Data()).isNotNull().isBase64();
    }

    @Test
    void gpt_image_2_edit_single_image_works() {
        OpenAiImageModel model = gptImage2Builder().build();

        Image source = solidColorPng(512, 512, new Color(255, 230, 180));

        Response<Image> response = model.edit(source, "Make the background a starry night sky");

        // gpt-image-* always returns b64_json regardless of any responseFormat the user set
        assertThat(response.content().base64Data()).isNotNull().isBase64();
        assertThat(response.content().url()).isNull();
    }

    @Test
    void gpt_image_2_edit_with_mask_works() {
        OpenAiImageModel model = gptImage2Builder().build();

        Image source = solidColorPng(512, 512, new Color(200, 255, 200));
        Image mask = transparentMask(512, 512);

        Response<Image> response =
                model.edit(source, mask, "Replace the masked area with a small red apple");

        assertThat(response.content().base64Data()).isNotNull().isBase64();
    }

    @Test
    void gpt_image_2_edit_multi_image_works() {
        OpenAiImageModel model = gptImage2Builder().build();

        List<Image> sources = List.of(
                solidColorPng(512, 512, new Color(255, 200, 200)),
                solidColorPng(512, 512, new Color(200, 200, 255)));

        Response<Image> response = model.edit(sources, "Combine both images into a single watercolor scene");

        assertThat(response.content().base64Data()).isNotNull().isBase64();
    }

    private OpenAiImageModel.OpenAiImageModelBuilder gptImage2Builder() {
        return OpenAiImageModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_IMAGE_2)
                .size("1024x1024")
                .logRequests(true)
                .logResponses(true);
    }

    private static Image solidColorPng(int width, int height, Color color) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        return Image.builder().base64Data(toPngBase64(img)).mimeType("image/png").build();
    }

    private static Image transparentMask(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            // fully opaque outside, transparent square in the middle (the editable area)
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, height);
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(width / 4, height / 4, width / 2, height / 2);
        } finally {
            g.dispose();
        }
        return Image.builder().base64Data(toPngBase64(img)).mimeType("image/png").build();
    }

    private static String toPngBase64(BufferedImage img) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void should_use_enum_as_model_name() {

        // given
        OpenAiImageModel model = OpenAiImageModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(DALL_E_2)
                .logRequests(true)
                .logResponses(true)
                .build();

        String prompt = "Beautiful house on country side";

        // when
        Response<Image> response = model.generate(prompt);

        // then
        URI remoteImage = response.content().url();
        log.info("Your remote image is here: {}", remoteImage);
        assertThat(remoteImage).isNotNull();
    }
}
