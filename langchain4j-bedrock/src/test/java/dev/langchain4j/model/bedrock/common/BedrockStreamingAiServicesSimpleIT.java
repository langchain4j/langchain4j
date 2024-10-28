package dev.langchain4j.model.bedrock.common;

import dev.langchain4j.model.bedrock.BedrockAnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.common.StreamingAiServicesSimpleIT;

import java.util.List;

import static dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1;

class BedrockStreamingAiServicesSimpleIT extends StreamingAiServicesSimpleIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                BedrockAnthropicStreamingChatModel.builder()
                        .model(AnthropicClaude3SonnetV1.getValue())
                        .build()
                // TODO add more models
        );
    }
}
