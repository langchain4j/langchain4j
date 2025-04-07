package dev.langchain4j.model.anthropic.common;

import static dev.langchain4j.model.anthropic.common.AnthropicChatModelIT.ANTHROPIC_CHAT_MODEL;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(ANTHROPIC_CHAT_MODEL);
    }
}
