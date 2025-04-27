package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1;
import static dev.langchain4j.model.bedrock.BedrockChatModelWithInvokeAPIIT.sleepIfNeeded;

class BedrockAiServicesWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                BedrockAnthropicMessageChatModel.builder()
                        .model(AnthropicClaude3SonnetV1.getValue())
                        .temperature(0.0f)
                        .build()
        );
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
