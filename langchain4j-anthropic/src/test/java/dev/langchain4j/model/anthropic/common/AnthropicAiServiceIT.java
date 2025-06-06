package dev.langchain4j.model.anthropic.common;

import static dev.langchain4j.model.anthropic.common.AnthropicChatModelIT.ANTHROPIC_CHAT_MODEL;

import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(ANTHROPIC_CHAT_MODEL);
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel chatModel) {
        return AnthropicTokenUsage.class;
    }
}
