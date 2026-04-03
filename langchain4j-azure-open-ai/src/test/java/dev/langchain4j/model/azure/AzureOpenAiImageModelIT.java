package dev.langchain4j.model.azure;

import static com.azure.ai.openai.models.ImageGenerationQuality.STANDARD;
import static com.azure.ai.openai.models.ImageGenerationResponseFormat.BASE64;
import static com.azure.ai.openai.models.ImageSize.SIZE1024X1024;
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

        AzureOpenAiImageModel model = AzureModelBuilders.imageModelBuilder()
                .deploymentName("dall-e-3-30")
                .size(SIZE1024X1024)
                .quality(STANDARD)
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

        ImageGenerationResponseFormat responseFormat = BASE64;

        AzureOpenAiImageModel model = AzureModelBuilders.imageModelBuilder()
                .deploymentName("dall-e-3-30")
                .size(SIZE1024X1024)
                .quality(STANDARD)
                .responseFormat(responseFormat)
                .logRequestsAndResponses(true)
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

        AzureOpenAiImageModel model = AzureModelBuilders.imageModelBuilder()
                .deploymentName(modelNameString)
                .size(SIZE1024X1024)
                .quality(STANDARD)
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
