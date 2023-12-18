package dev.langchain4j.model.openai;

import static dev.ai4j.openai4j.image.ImageModel.DALL_E_QUALITY_HD;
import static dev.ai4j.openai4j.image.ImageModel.DALL_E_RESPONSE_FORMAT_B64_JSON;
import static dev.ai4j.openai4j.image.ImageModel.DALL_E_SIZE_256_x_256;
import static dev.langchain4j.model.openai.OpenAiModelName.DALL_E_2;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import java.io.File;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OpenAiImagesGenerationIT {

    Logger log = LoggerFactory.getLogger(OpenAiImagesGenerationIT.class);

    OpenAiImageModel.OpenAiImageModelBuilder modelBuilder = OpenAiImageModel
        .builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .model(DALL_E_2) // so that you pay not much :)
        .size(DALL_E_SIZE_256_x_256)
        .logRequests()
        .logResponses();

    @Test
    void simple_image_generation_works() {
        OpenAiImageModel model = modelBuilder.build();

        Response<List<Image>> response = model.generate("Beautiful house on country side");

        assertThat(response.content()).hasSize(1);

        URI remoteImage = response.content().get(0).url();
        log.info("Your remote image is here: {}", remoteImage);
        assertThat(remoteImage).isNotNull();

        String revisedPrompt = response.content().get(0).revisedPrompt();
        log.info("Your revised prompt: {}", revisedPrompt);
        assertThat(revisedPrompt).hasSizeGreaterThan(5);
    }

    @Test
    void image_generation_with_persisting_works() {
        OpenAiImageModel model = modelBuilder.withPersisting().build();

        Response<List<Image>> response = model.generate("Bird flying in the sky");

        assertThat(response.content()).hasSize(1);

        URI localImage = response.content().get(0).url();
        log.info("Your local image is here: {}", localImage);
        assertThat(new File(localImage)).exists();
    }

    @Test
    void multiple_images_generation_with_base64_works() {
        OpenAiImageModel model = modelBuilder
            .n(2)
            .responseFormat(DALL_E_RESPONSE_FORMAT_B64_JSON)
            .withPersisting()
            .build();

        Response<List<Image>> response = model.generate("Cute red parrot sings");

        assertThat(response.content()).hasSize(2);

        URI localImage1 = response.content().get(0).url();
        log.info("Your first local image is here: {}", localImage1);
        assertThat(new File(localImage1)).exists();

        URI localImage2 = response.content().get(1).url();
        log.info("Your second local image is here: {}", localImage2);
        assertThat(new File(localImage2)).exists();
    }

    @Test
    void image_generation_with_dalle3_works() {
        OpenAiImageModel model = OpenAiImageModel
            .builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .quality(DALL_E_QUALITY_HD)
            .logRequests()
            .logResponses()
            .build();

        Response<List<Image>> response = model.generate(
            "Beautiful house on country side, cowboy plays guitar, dog sitting at the door"
        );

        assertThat(response.content()).hasSize(1);

        URI remoteImage = response.content().get(0).url();
        log.info("Your remote image is here: {}", remoteImage);
        assertThat(remoteImage).isNotNull();

        String revisedPrompt = response.content().get(0).revisedPrompt();
        log.info("Your revised prompt: {}", revisedPrompt);
        assertThat(revisedPrompt).hasSizeGreaterThan(50);
    }
}
