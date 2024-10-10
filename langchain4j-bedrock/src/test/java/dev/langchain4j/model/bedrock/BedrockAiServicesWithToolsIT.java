package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;

import java.util.List;

import static dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1;
import static java.util.Collections.singletonList;

class BedrockAiServicesWithToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return singletonList(BedrockAnthropicMessageChatModel.builder()
                .model(AnthropicClaude3SonnetV1.getValue())
                .temperature(0.0f)
                .build());
    }

    @Override
    protected boolean verifyModelInteractions() {
        return false;
    }
}
