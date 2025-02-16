package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModelIT.OPEN_AI_CHAT_MODEL;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModelIT.OPEN_AI_CHAT_MODEL_STRICT_SCHEMA;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;

class OpenAiOfficialAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                OPEN_AI_CHAT_MODEL,
                OPEN_AI_CHAT_MODEL_STRICT_SCHEMA);
    }
}
