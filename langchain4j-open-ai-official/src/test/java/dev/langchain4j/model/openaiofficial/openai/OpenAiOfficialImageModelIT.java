package dev.langchain4j.model.openaiofficial.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialImageModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Run manually before release. Expensive to run very often.")
class OpenAiOfficialImageModelIT {

    protected List<ImageModel> models() {
        return InternalOpenAiOfficialTestHelper.imageModelsUrl();
    }

    @Test
    void should_generate_image() {
        for (ImageModel model : models()) {
            Response<Image> response = model.generate("A cup of coffee on a table in Paris, France");

            Image image = response.content();
            assertThat(image).isNotNull();
            assertThat(image.base64Data()).isNotNull().isNotBlank();
            assertThat(image.mimeType()).isNotNull();
        }
    }

    @Test
    void should_generate_image_with_output_format() {
        OpenAiOfficialImageModel model = OpenAiOfficialImageModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(InternalOpenAiOfficialTestHelper.IMAGE_MODEL_NAME)
                .outputFormat("png")
                .build();

        Response<Image> response = model.generate("A cup of coffee on a table in Paris, France");

        Image image = response.content();
        assertThat(image).isNotNull();
        assertThat(image.base64Data()).isNotNull().isNotBlank();
        assertThat(image.mimeType()).isEqualTo("image/png");
    }

    @Test
    void should_generate_image_with_transparent_background() {
        OpenAiOfficialImageModel model = OpenAiOfficialImageModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(InternalOpenAiOfficialTestHelper.IMAGE_MODEL_NAME)
                .background("transparent")
                .outputFormat("png")
                .build();

        Response<Image> response = model.generate("A red apple");

        Image image = response.content();
        assertThat(image).isNotNull();
        assertThat(image.base64Data()).isNotNull().isNotBlank();
        assertThat(image.mimeType()).isEqualTo("image/png");
    }
}
