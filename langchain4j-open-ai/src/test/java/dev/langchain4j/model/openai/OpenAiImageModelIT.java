package dev.langchain4j.model.openai;

import static dev.langchain4j.model.openai.OpenAiImageModelName.GPT_IMAGE_1;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled("Run manually before release. Expensive to run very often.")
class OpenAiImageModelIT {

    Logger log = LoggerFactory.getLogger(OpenAiImageModelIT.class);

    OpenAiImageModel.OpenAiImageModelBuilder modelBuilder = OpenAiImageModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
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
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
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
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
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
    void should_use_enum_as_model_name() {

        // given
        OpenAiImageModel model = OpenAiImageModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
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
