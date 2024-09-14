package dev.langchain4j.model.spark;

import dev.langchain4j.model.output.Response;
import io.github.briqt.spark4j.constant.SparkApiVersion;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * @author ren
 * @since 2024/3/15 15:17
 */
@EnabledIfEnvironmentVariable(named = "SPARK_API_KEY", matches = ".+")
@Slf4j
class SparkLanguageModelIT {
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
    void generate() {
        log.info("appId:{} apiKey:{}  ", appId, apiKey);
        SparkLanguageModel model = SparkLanguageModel.builder()
                .appId(appId)
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .apiVersion(SparkApiVersion.V3_5)
                .build();
        Response<String> response = model.generate("hello");
        Assertions.assertNotNull(response.content());
    }
}