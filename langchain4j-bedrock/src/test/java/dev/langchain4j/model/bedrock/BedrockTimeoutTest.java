package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkClient;

class BedrockTimeoutTest {

    @Test
    void should_NOT_limit_stream_duration_when_timeout_is_not_set() throws Exception {

        BedrockStreamingChatModel model =
                BedrockStreamingChatModel.builder().modelId("test-model").build();

        assertThat(apiCallTimeout(model)).isEmpty();
    }

    @Test
    void should_limit_stream_duration_when_timeout_is_set() throws Exception {

        BedrockStreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId("test-model")
                .timeout(Duration.ofSeconds(5))
                .build();

        assertThat(apiCallTimeout(model)).contains(Duration.ofSeconds(5));
    }

    @Test
    void should_keep_one_minute_default_timeout_for_chat_model() throws Exception {

        BedrockChatModel model =
                BedrockChatModel.builder().modelId("test-model").build();

        assertThat(apiCallTimeout(model)).contains(Duration.ofMinutes(1));
    }

    private static Optional<Duration> apiCallTimeout(Object model) throws Exception {
        Field field = model.getClass().getDeclaredField("client");
        field.setAccessible(true);
        try (SdkClient client = (SdkClient) field.get(model)) {
            return client.serviceClientConfiguration().overrideConfiguration().apiCallTimeout();
        }
    }
}
