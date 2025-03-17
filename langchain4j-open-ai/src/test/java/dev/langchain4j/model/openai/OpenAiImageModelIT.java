package dev.langchain4j.model.openai;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.List;

import static dev.ai4j.openai4j.image.ImageModel.*;
import static dev.langchain4j.model.openai.OpenAiImageModelName.DALL_E_2;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Run manually before release. Expensive to run very often.")
class OpenAiImageModelIT {

    Logger log = LoggerFactory.getLogger(OpenAiImageModelIT.class);

    OpenAiImageModel.OpenAiImageModelBuilder modelBuilder = OpenAiImageModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(DALL_E_2) // so that you pay not much :)
            .size(DALL_E_SIZE_256_x_256)
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
    void image_generation_with_persisting_works() {
        OpenAiImageModel model = modelBuilder.responseFormat(DALL_E_RESPONSE_FORMAT_B64_JSON).withPersisting().build();

        Response<Image> response = model.generate("Bird flying in the sky");

        URI localImage = response.content().url();
        log.info("Your local image is here: {}", localImage);
        assertThat(new File(localImage)).exists();
    }

    @Test
    void multiple_images_generation_with_base64_works() {
        OpenAiImageModel model = modelBuilder.responseFormat(DALL_E_RESPONSE_FORMAT_B64_JSON).withPersisting().build();

        Response<List<Image>> response = model.generate("Cute red parrot sings", 2);

        assertThat(response.content()).hasSize(2);

        Image localImage1 = response.content().get(0);
        log.info("Your first local image is here: {}", localImage1.url());
        assertThat(new File(localImage1.url())).exists();
        assertThat(localImage1.base64Data()).isNotNull().isBase64();

        Image localImage2 = response.content().get(1);
        log.info("Your second local image is here: {}", localImage2.url());
        assertThat(new File(localImage2.url())).exists();
        assertThat(localImage2.base64Data()).isNotNull().isBase64();
    }

    @Test
    void image_generation_with_dalle3_works() {
        OpenAiImageModel model = OpenAiImageModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .quality(DALL_E_QUALITY_HD)
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
