package dev.langchain4j.model.bedrock.common;

import dev.langchain4j.model.bedrock.BedrockAnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;

import java.util.List;

import static dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1;

class BedrockStreamingChatModelIT extends AbstractStreamingChatModelIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                BedrockAnthropicStreamingChatModel.builder()
                        .model(AnthropicClaude3SonnetV1.getValue())
                        .build()
                // TODO add more models
        );
    }

    @Override
    protected boolean supportsTools() {
        return false;
    }

    @Override
    protected boolean supportsToolChoice() {
        return false;
    }
}
