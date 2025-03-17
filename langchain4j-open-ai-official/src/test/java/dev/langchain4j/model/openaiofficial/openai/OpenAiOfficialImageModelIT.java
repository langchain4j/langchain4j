package dev.langchain4j.model.openaiofficial.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@Disabled("Run manually before release. Expensive to run very often.")
class OpenAiOfficialImageModelIT {

    protected List<ImageModel> modelsUrl() {
        return InternalOpenAiOfficialTestHelper.imageModelsUrl();
    }

    @Test
    void should_generate_image_with_url() {
        for (ImageModel model : modelsUrl()) {
            Response<Image> response = model.generate("A cup of coffee on a table in Paris, France");

            Image image = response.content();
            assertThat(image).isNotNull();
            assertThat(image.url()).isNotNull();
            assertThat(image.base64Data()).isNull();
            assertThat(image.revisedPrompt()).isNotNull();
            System.out.println("The image is here: " + image.url());
        }
    }
}
