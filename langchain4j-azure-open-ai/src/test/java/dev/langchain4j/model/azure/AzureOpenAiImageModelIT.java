package dev.langchain4j.model.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import com.azure.ai.openai.models.ImageGenerationResponseFormat;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Disabled("Run manually before release. Expensive to run very often.")
class AzureOpenAiImageModelIT {

    @Test
    void should_generate_image_with_url() {

        AzureOpenAiImageModel model = AzureOpenAiImageModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName("dall-e-3-30")
                .logRequestsAndResponses(true)
                .build();

        Response<Image> response = model.generate("A coffee mug in Paris, France");

        Image image = response.content();
        assertThat(image).isNotNull();
        assertThat(image.url()).isNotNull();
        assertThat(image.base64Data()).isNull();
        assertThat(image.revisedPrompt()).isNotNull();
    }

    @Test
    void should_generate_image_in_base64() throws IOException {
        AzureOpenAiImageModel model = AzureOpenAiImageModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName("dall-e-3-30")
                .logRequestsAndResponses(false) // The image is big, so we don't want to log it by default
                .responseFormat(ImageGenerationResponseFormat.BASE64.toString())
                .build();

        Response<Image> response = model.generate("A croissant in Paris, France");

        Image image = response.content();
        assertThat(image).isNotNull();
        assertThat(image.url()).isNull();
        assertThat(image.base64Data()).isNotNull();

        if (false) {
            byte[] decodedBytes = Base64.getDecoder().decode(response.content().base64Data());
            Path temp = Files.createTempFile("langchain4j", ".png");
            Files.write(temp, decodedBytes);
            System.out.println("The image is here: " + temp.toAbsolutePath());
        }

        assertThat(image.revisedPrompt()).isNotNull();
    }

    @ParameterizedTest(name = "Testing model {0}")
    @EnumSource(value = AzureOpenAiImageModelName.class, mode = EXCLUDE, names = "DALL_E_3")
    void should_support_all_string_model_names(AzureOpenAiImageModelName modelName) {

        // given
        String modelNameString = modelName.toString();

        AzureOpenAiImageModel model = AzureOpenAiImageModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(modelNameString)
                .logRequestsAndResponses(true)
                .build();

        // when
        Response<Image> response = model.generate("A coffee mug in Paris, France");

        // then
        Image image = response.content();
        assertThat(image).isNotNull();
        assertThat(image.url()).isNotNull();
        assertThat(image.base64Data()).isNull();
        assertThat(image.revisedPrompt()).isNotNull();
    }
}
