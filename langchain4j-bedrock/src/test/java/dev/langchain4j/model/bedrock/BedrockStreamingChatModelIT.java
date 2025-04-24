package dev.langchain4j.model.bedrock;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;

import java.util.List;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockStreamingChatModelIT {

    @Test
    void bedrockAnthropicStreamingChatModel() {
        // given
        BedrockAnthropicStreamingChatModel bedrockChatModel = BedrockAnthropicStreamingChatModel.builder()
                .temperature(0.5)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .build();
        UserMessage userMessage = userMessage("What's the capital of Poland?");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        bedrockChatModel.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).contains("Warsaw");
    }

    @Test
    void injectClientToModelBuilder() {

        String serviceName = "custom-service-name";

        BedrockAnthropicStreamingChatModel model = BedrockAnthropicStreamingChatModel.builder()
                .asyncClient(new BedrockRuntimeAsyncClient() {
                    @Override
                    public String serviceName() {
                        return serviceName;
                    }

                    @Override
                    public void close() {}
                })
                .build();

        assertThat(model.getAsyncClient().serviceName()).isEqualTo(serviceName);
    }
}
