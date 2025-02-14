package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.models.ImageGenerateParams;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Run manually before release. Expensive to run very often.")
public class OpenAiOfficialImageModelIT {

    public static final com.openai.models.ImageModel MODEL_NAME = com.openai.models.ImageModel.DALL_E_3;

    ImageModel model = OpenAiOfficialImageModel.builder()
            .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
            .size(ImageGenerateParams.Size._1024X1024)
            .modelName(MODEL_NAME)
            .build();

    ImageModel modelBase64 = OpenAiOfficialImageModel.builder()
            .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
            .modelName(MODEL_NAME)
            .responseFormat(ImageGenerateParams.ResponseFormat.B64_JSON)
            .build();

    @Test
    void should_generate_image_with_url() {

        Response<Image> response = model.generate("A church turned into a coworking space in Paris, France");

        Image image = response.content();
        assertThat(image).isNotNull();
        assertThat(image.url()).isNotNull();
        assertThat(image.base64Data()).isNull();
        assertThat(image.revisedPrompt()).isNotNull();
        System.out.println("The image is here: " + image.url());
    }

    @Test
    void should_generate_image_in_base64() throws IOException {
        Response<Image> response = modelBase64.generate("A croissant in Paris, France");

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
}
