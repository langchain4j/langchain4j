package dev.langchain4j.model.azure.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.azure.common.AzureOpenAiChatModelIT.AZURE_OPEN_AI_CHAT_MODEL;

class AzureOpenAiAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                AZURE_OPEN_AI_CHAT_MODEL
                // TODO add more model configs
        );
    }
}
