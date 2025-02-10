package dev.langchain4j.model.openaiofficial;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;

import java.util.List;

import static dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModelIT.OPEN_AI_CHAT_MODEL;

class OpenAiOfficialAiServicesWithToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                OPEN_AI_CHAT_MODEL
        );
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }
}
