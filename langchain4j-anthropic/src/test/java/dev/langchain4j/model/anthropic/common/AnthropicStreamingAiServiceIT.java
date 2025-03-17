package dev.langchain4j.model.anthropic.common;

import static dev.langchain4j.model.anthropic.common.AnthropicStreamingChatModelIT.ANTHROPIC_STREAMING_CHAT_MODEL;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(ANTHROPIC_STREAMING_CHAT_MODEL);
    }
}
