package dev.langchain4j.model.openaiofficial;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithJsonSchemaIT;

import java.util.List;

import static dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModelIT.OPEN_AI_CHAT_MODEL_STRICT_SCHEMA;

class OpenAiOfficialAiServicesWithJsonSchemaIT extends AiServicesWithJsonSchemaIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                OPEN_AI_CHAT_MODEL_STRICT_SCHEMA
        );
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

}
