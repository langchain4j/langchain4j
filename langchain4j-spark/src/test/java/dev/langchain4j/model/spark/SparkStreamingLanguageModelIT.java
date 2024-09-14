package dev.langchain4j.model.spark;

import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * @author ren
 * @since 2024/3/18 14:20
 */
@EnabledIfEnvironmentVariable(named = "SPARK_API_KEY", matches = ".+")
class SparkStreamingLanguageModelIT {
    private String appId;
    private String apiKey;
    private String apiSecret;

    @BeforeEach
    public void before() {
        appId = System.getenv("SPARK_APPID");
        apiKey = System.getenv("SPARK_API_KEY");
        apiSecret = System.getenv("SPARK_API_SECRET");
    }

    @Test
    void should_generate() {
        SparkStreamingLanguageModel model = SparkStreamingLanguageModel.builder()
                .appId(appId)
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .build();
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        model.generate("Where is the capital of China? Please answer in English", handler);
        Response<String> response = handler.get();
        Assertions.assertNotNull(response);
        Assertions.assertTrue(response.tokenUsage().outputTokenCount() > 0);
        Assertions.assertTrue(response.content().toLowerCase().contains("beijing"));
    }

}