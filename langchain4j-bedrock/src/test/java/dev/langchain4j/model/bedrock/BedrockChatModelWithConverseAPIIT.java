package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.bedrock.converse.BedrockChatModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
public class BedrockChatModelWithConverseAPIIT {

    @Test
    void testBedrockChatModelWithDefaultConfig() {
        BedrockChatModel bedrockChatModel = new BedrockChatModel();

        assertThat(bedrockChatModel).isNotNull();

        Response<AiMessage> response = bedrockChatModel.generate(UserMessage.from("hi, how are you doing?"));

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void testBedrockChatModelWithDefaultConfigAndSystemMessage() {
        BedrockChatModel bedrockChatModel = new BedrockChatModel();

        assertThat(bedrockChatModel).isNotNull();

        SystemMessage systemMessage = SystemMessage.from("You are very helpful assistant. Answer client's question.");
        UserMessage userMessage = UserMessage.from("hi, how are you doing?");

        Response<AiMessage> response = bedrockChatModel.generate(systemMessage, userMessage);

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }
}
