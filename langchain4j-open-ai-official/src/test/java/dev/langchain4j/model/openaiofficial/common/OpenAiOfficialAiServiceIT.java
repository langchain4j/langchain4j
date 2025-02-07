package dev.langchain4j.model.openaiofficial.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.openaiofficial.common.OpenAiOfficialChatModelIT.OPEN_AI_CHAT_MODEL_STRICT_SCHEMA;

class OpenAiOfficialAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                //OPEN_AI_CHAT_MODEL, //TODO FIX this doesn't run reliably when generating JSON (as there is no schema)
                OPEN_AI_CHAT_MODEL_STRICT_SCHEMA
        );
    }
}
