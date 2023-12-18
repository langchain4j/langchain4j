package dev.langchain4j.model.azure;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AzureOpenAiImageModelIT {

    Logger logger = LoggerFactory.getLogger(AzureOpenAiImageModelIT.class);

    @Test
    void should_generate_image() {

        AzureOpenAiImageModel model = AzureOpenAiImageModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .logRequestsAndResponses(true)
                .setN(1)
                .build();

        Response<List<Image>> response = model.generate("A coffee mug in Paris, France");

        logger.info(response.toString());

        Image image = response.content().get(0);
        assertNotNull(image.revisedPrompt());
        assertNotNull(image.url());
    }
}
