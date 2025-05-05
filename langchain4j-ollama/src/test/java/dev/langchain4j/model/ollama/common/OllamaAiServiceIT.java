package dev.langchain4j.model.ollama.common;

import static dev.langchain4j.model.ollama.common.OllamaChatModelIT.OLLAMA_CHAT_MODEL_WITH_TOOLS;
import static dev.langchain4j.model.ollama.common.OllamaChatModelIT.OPEN_AI_CHAT_MODEL_WITH_TOOLS;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;

class OllamaAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(OLLAMA_CHAT_MODEL_WITH_TOOLS, OPEN_AI_CHAT_MODEL_WITH_TOOLS);
    }

    @Override
    protected boolean assertToolInteractions() {
        return false; // TODO fix
    }
}
