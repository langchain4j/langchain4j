package dev.langchain4j.model.mistralai.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.mistralai.common.MistralAiChatModelIT.MISTRAL_CHAT_MODEL;

class MistralAiAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                MISTRAL_CHAT_MODEL
        );
    }
}
