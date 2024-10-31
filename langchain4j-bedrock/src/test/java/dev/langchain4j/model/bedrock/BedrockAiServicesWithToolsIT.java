package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;

import java.util.List;

import static dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1;

class BedrockAiServicesWithToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                BedrockAnthropicMessageChatModel.builder()
                        .model(AnthropicClaude3SonnetV1.getValue())
                        .temperature(0.0f)
                        .build()
        );
    }
}
