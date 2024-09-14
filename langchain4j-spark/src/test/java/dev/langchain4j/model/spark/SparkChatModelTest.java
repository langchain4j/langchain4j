package dev.langchain4j.model.spark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author ren
 * @since 2024/3/18 13:37
 */
@EnabledIfEnvironmentVariable(named = "SPARK_API_KEY", matches = ".+")
class SparkChatModelTest {

    private String appId;
    private String apiKey;
    private String apiSecret;

    @BeforeEach
    public void setUp() {
        appId = System.getenv("SPARK_APPID");
        apiKey = System.getenv("SPARK_API_KEY");
        apiSecret = System.getenv("SPARK_API_SECRET");
    }

    @Test
    public void chat() {
        SparkChatModel model = SparkChatModel.builder()
                .appId(appId)
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .build();
        assertNotNull(model.generate("hello"));
    }
}