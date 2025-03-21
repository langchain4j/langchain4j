package dev.langchain4j.model.ollama.common;

import static dev.langchain4j.model.ollama.common.OllamaChatModelIT.OLLAMA_CHAT_MODEL_WITH_TOOLS;
import static dev.langchain4j.model.ollama.common.OllamaChatModelIT.OPEN_AI_CHAT_MODEL_WITH_TOOLS;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;

class OllamaAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(OLLAMA_CHAT_MODEL_WITH_TOOLS.model(), OPEN_AI_CHAT_MODEL_WITH_TOOLS.model());
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertToolInteractions() {
        return false; // TODO fix
    }
}
