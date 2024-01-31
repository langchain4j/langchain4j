package dev.langchain4j.model.azure;

import com.azure.ai.openai.models.ImageGenerationResponseFormat;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Disabled
public class AzureOpenAiImageModelIT {

    Logger logger = LoggerFactory.getLogger(AzureOpenAiImageModelIT.class);

    @Test
    void should_generate_image_with_url() {

        AzureOpenAiImageModel model = AzureOpenAiImageModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .logRequestsAndResponses(true)
                .build();

        Response<Image> response = model.generate("A coffee mug in Paris, France");

        logger.info(response.toString());

        Image image = response.content();
        assertNotNull(image);
        assertNotNull(image.url());
        logger.info("The remote image is here: {}", image.url());

        assertNull(image.base64Data());

        assertNotNull(image.revisedPrompt());
        logger.info("The revised prompt is: {}", image.revisedPrompt());
    }

    @Test
    void should_generate_image_in_base64() throws IOException {
        AzureOpenAiImageModel model = AzureOpenAiImageModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .logRequestsAndResponses(false) // The image is big, so we don't want to log it by default
                .responseFormat(ImageGenerationResponseFormat.BASE64.toString())
                .build();

        Response<Image> response = model.generate("A croissant in Paris, France");

        Image image = response.content();
        assertNotNull(image);
        assertNull(image.url());
        assertNotNull(image.base64Data());
        logger.info("The image data is: {} characters", image.base64Data().length());

        if (logger.isDebugEnabled()) {
            byte[] decodedBytes = Base64.getDecoder().decode(response.content().base64Data());
            Path temp = Files.createTempFile("langchain4j", ".png");
            Files.write(temp, decodedBytes);
            logger.debug("The image is here: {}", temp.toAbsolutePath());
        }

        assertNotNull(image.revisedPrompt());
        logger.info("The revised prompt is: {}", image.revisedPrompt());
    }
}
