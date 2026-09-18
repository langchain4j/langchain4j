package dev.langchain4j.model.watsonx.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.OutputFormat;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Quality;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Size;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.watsonx.WatsonxGatewayImageModel;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GATEWAY_IMAGE_MODEL", matches = ".+")
public class WatsonxGatewayImageModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String URL = System.getenv("WATSONX_URL");
    static final String MODEL = System.getenv("WATSONX_GATEWAY_IMAGE_MODEL");

    static final WatsonxGatewayImageModel model = WatsonxGatewayImageModel.builder()
            .baseUrl(URL)
            .apiKey(API_KEY)
            .modelName(MODEL)
            .size(Size.SIZE_1024X1024)
            .quality(Quality.LOW)
            .build();

    @Test
    void should_generate_an_image() {

        var response = model.generate("A red cube on a white background");
        var image = response.content();

        assertEquals(MODEL, model.modelName());
        assertNotNull(assertDecodableBase64Data(image));
    }

    @Test
    void should_generate_more_than_one_image() {

        var response = model.generate("A blue sphere on a white background", 2);

        assertEquals(2, response.content().size());
        response.content().forEach(this::assertDecodableBase64Data);
    }

    @Test
    void should_generate_an_image_with_the_given_parameters() {

        var response = model.generate(
                "A green pyramid on a white background",
                ModelGatewayImageParameters.builder()
                        .size(Size.SIZE_1024X1024)
                        .quality(Quality.LOW)
                        .outputFormat(OutputFormat.PNG)
                        .n(1)
                        .build());

        var image = response.content().get(0);

        assertEquals(1, response.content().size());
        assertEquals("image/png", image.mimeType());
        assertDecodableBase64Data(image);
    }

    @Test
    void should_fail_when_the_size_is_not_supported() {

        var parameters = ModelGatewayImageParameters.builder().size("123x456").build();

        assertThrows(
                InvalidRequestException.class, () -> model.generate("A yellow cone on a white background", parameters));
    }

    @Test
    void should_not_support_the_image_editing() {

        var image = Image.builder().base64Data("aGVsbG8=").mimeType("image/png").build();

        assertThrows(IllegalArgumentException.class, () -> model.edit(image, "Make it brighter"));
        assertThrows(IllegalArgumentException.class, () -> model.edit(image, image, "Make it brighter"));
    }

    private byte[] assertDecodableBase64Data(Image image) {

        // The gpt-image-1 model always answers with Base64 data, it does not support the url response format.
        assertNull(image.url());
        assertNotNull(image.base64Data());

        var bytes = Base64.getDecoder().decode(image.base64Data());
        assertTrue(bytes.length > 0);

        return bytes;
    }
}
