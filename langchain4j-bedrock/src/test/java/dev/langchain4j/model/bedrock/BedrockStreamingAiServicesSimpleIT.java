package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.StreamingAiServicesSimpleIT;

import java.util.List;

import static dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1;

class BedrockStreamingAiServicesSimpleIT extends StreamingAiServicesSimpleIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                BedrockAnthropicStreamingChatModel.builder()
                        .model(AnthropicClaude3SonnetV1.getValue())
                        .build()
        );
    }
}
