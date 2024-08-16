package dev.langchain4j.model.zhipu;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
public class ZhipuAiImageModelIT {
    private static final Logger log = LoggerFactory.getLogger(ZhipuAiImageModelIT.class);
    private static final String apiKey = System.getenv("ZHIPU_API_KEY");

    private final ZhipuAiImageModel model = ZhipuAiImageModel.builder()
            .apiKey(apiKey)
            .logRequests(true)
            .logResponses(true)
            .callTimeout(Duration.ofSeconds(60))
            .connectTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .readTimeout(Duration.ofSeconds(60))
            .build();

    @Test
    void simple_image_generation_works() {
        Response<Image> response = model.generate("Beautiful house on country side");

        URI remoteImage = response.content().url();
        log.info("Your remote image is here: {}", remoteImage);
        assertThat(remoteImage).isNotNull();
    }
}