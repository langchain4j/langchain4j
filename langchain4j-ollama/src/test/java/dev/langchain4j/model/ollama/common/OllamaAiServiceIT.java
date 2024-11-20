package dev.langchain4j.model.ollama.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.ollama.common.OllamaChatModelIT.OLLAMA_CHAT_MODEL;
import static dev.langchain4j.model.ollama.common.OllamaChatModelIT.OPEN_AI_CHAT_MODEL;

class OllamaAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(OLLAMA_CHAT_MODEL, OPEN_AI_CHAT_MODEL);
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO fix
    }
}
