package dev.langchain4j.model.openai;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

import static dev.langchain4j.model.openai.OpenAiImageModelName.DALL_E_2;
import static dev.langchain4j.model.openai.OpenAiImageModelName.DALL_E_3;
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
