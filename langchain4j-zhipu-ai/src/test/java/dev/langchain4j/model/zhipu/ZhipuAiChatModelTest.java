package dev.langchain4j.model.zhipu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class ZhipuAiChatModelTest {
    private static final String apiKey = "fake-zhipu-apikey";

    @Test
    void build_model_with_default_values() {
        assertDoesNotThrow(() ->
                ZhipuAiChatModel.builder()
                        .apiKey(apiKey) // only apiKey is necessary
                        .build()
        );
    }

    @Test
    void build_model_with_null_values() {
        assertDoesNotThrow(() ->
                ZhipuAiChatModel.builder()
                        .baseUrl(null) // use default url
                        .apiKey(apiKey)
                        .callTimeout(null) // use default timeout
                        .connectTimeout(null) // use default timeout
                        .readTimeout(null) // use default timeout
                        .writeTimeout(null) // use default timeout
                        .logRequests(null) // use default one
                        .logResponses(null) // use default one
                        .build()
        );
    }
}