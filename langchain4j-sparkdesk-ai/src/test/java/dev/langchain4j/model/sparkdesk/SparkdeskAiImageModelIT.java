package dev.langchain4j.model.sparkdesk;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SPARKDESK_API_KEY", matches = ".+")
public class SparkdeskAiImageModelIT {
    private static final Logger log = LoggerFactory.getLogger(SparkdeskAiImageModelIT.class);
    private static final String API_KEY = System.getenv("SPARKDESK_API_KEY");
    private static final String API_SECRET = System.getenv("SPARKDESK_API_SECRET");
    private static final String APP_ID = System.getenv("SPARKDESK_API_ID");

    private final SparkdeskAiImageModel model = SparkdeskAiImageModel.builder()
            .apiKey(API_KEY)
            .apiSecret(API_SECRET)
            .appId(APP_ID)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void simple_image_generation_works() {
        Response<Image> response = model.generate("Beautiful house on country side");
        String base64Data = response.content().base64Data();
        log.info("base64Data: {}", base64Data);
        assertThat(base64Data).isNotNull();
        assertThat(response.finishReason()).isNull();
    }
}