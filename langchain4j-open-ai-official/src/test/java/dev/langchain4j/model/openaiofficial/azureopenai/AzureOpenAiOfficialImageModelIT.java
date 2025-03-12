package dev.langchain4j.model.openaiofficial.azureopenai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
@Disabled("Run manually before release. Expensive to run very often.")
class AzureOpenAiOfficialImageModelIT {

    protected List<ImageModel> modelsUrl() {
        return InternalAzureOpenAiOfficialTestHelper.imageModelsUrl();
    }

    protected List<ImageModel> modelsBase64() {
        return InternalAzureOpenAiOfficialTestHelper.imageModelsBase64();
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

    @Test
    void should_generate_image_in_base64() throws IOException {
        for (ImageModel model : modelsBase64()) {
            Response<Image> response = model.generate("A croissant in Paris, France");

            Image image = response.content();
            assertThat(image).isNotNull();
            assertThat(image.url()).isNull();
            assertThat(image.base64Data()).isNotNull();

            /*
            // Uncomment this block to save the image to a file, so you can see it.
            byte[] decodedBytes = Base64.getDecoder().decode(response.content().base64Data());
            Path temp = Files.createTempFile("langchain4j", ".png");
            Files.write(temp, decodedBytes);
            System.out.println("The image is here: " + temp.toAbsolutePath());
            */

            assertThat(image.revisedPrompt()).isNotNull();
        }
    }
}
