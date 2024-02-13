package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class BedrockStreamingChatModelIT {
    @Test
    @Disabled("To run this test, you must have provide your own access key, secret, region")
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
