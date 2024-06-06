package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockStreamingChatModelIT {

    @Test
    void testBedrockAnthropicStreamingChatModel() {
        //given
        BedrockAnthropicStreamingChatModel bedrockChatModel = BedrockAnthropicStreamingChatModel
                .builder()
                .temperature(0.5)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .maxRetries(1)
                .build();
        UserMessage userMessage = userMessage("What's the capital of Poland?");

        //when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        bedrockChatModel.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        //then
        assertThat(response.content().text()).contains("Warsaw");
    }
}
