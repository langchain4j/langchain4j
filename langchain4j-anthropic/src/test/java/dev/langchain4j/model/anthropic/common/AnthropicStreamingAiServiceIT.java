package dev.langchain4j.model.anthropic.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.anthropic.common.AnthropicStreamingChatModelIT.ANTHROPIC_STREAMING_CHAT_MODEL;

class AnthropicStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                ANTHROPIC_STREAMING_CHAT_MODEL
        );
    }
}
