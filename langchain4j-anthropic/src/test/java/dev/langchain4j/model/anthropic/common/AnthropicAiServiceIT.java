package dev.langchain4j.model.anthropic.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.anthropic.common.AnthropicChatModelIT.ANTHROPIC_CHAT_MODEL;

class AnthropicAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                ANTHROPIC_CHAT_MODEL
        );
    }
}
