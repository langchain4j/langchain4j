package dev.langchain4j.model.bedrock.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.bedrock.common.BedrockChatModelIT.BEDROCK_ANTHROPIC_MESSAGE_CHAT_MODEL;

class BedrockAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                BEDROCK_ANTHROPIC_MESSAGE_CHAT_MODEL
                // TODO add more models from other providers
        );
    }

    protected boolean supportsJsonResponseFormatWithSchema() {
        return false; // TODO implement
    }
}
