package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.Utils.readBytes;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.bedrock.converse.BedrockChatModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
public class BedrockChatModelWithConverseAPIIT {

    private static final String CAT_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";

    @Test
    void testBedrockChatModelWithDefaultConfig() {
        BedrockChatModel bedrockChatModel = new BedrockChatModel("anthropic.claude-3-5-sonnet-20240620-v1:0");

        assertThat(bedrockChatModel).isNotNull();

        Response<AiMessage> response = bedrockChatModel.generate(UserMessage.from("hi, how are you doing?"));

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void testBedrockChatModelWithDefaultConfigAndSystemMessage() {
        BedrockChatModel bedrockChatModel = new BedrockChatModel("anthropic.claude-3-5-sonnet-20240620-v1:0");

        assertThat(bedrockChatModel).isNotNull();

        SystemMessage systemMessage = SystemMessage.from("You are very helpful assistant. Answer client's question.");
        UserMessage userMessage = UserMessage.from("hi, how are you doing?");

        Response<AiMessage> response = bedrockChatModel.generate(systemMessage, userMessage);

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void testBedrockChatModelWithDefaultConfigAndImageContent() {
        BedrockChatModel bedrockChatModel = new BedrockChatModel("anthropic.claude-3-5-sonnet-20240620-v1:0");

        assertThat(bedrockChatModel).isNotNull();

        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        ImageContent imageContent = ImageContent.from(base64Data, "image/png");
        UserMessage userMessage = UserMessage.from(imageContent);

        Response<AiMessage> response = bedrockChatModel.generate(userMessage);

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }
}
