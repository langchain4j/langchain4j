package dev.langchain4j.model.ollama.common;

import static dev.langchain4j.model.ollama.common.OllamaStreamingChatModelIT.OLLAMA_CHAT_MODEL_WITH_TOOLS;
import static dev.langchain4j.model.ollama.common.OllamaStreamingChatModelIT.OPEN_AI_CHAT_MODEL_WITH_TOOLS;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import java.util.List;

class OllamaStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(OLLAMA_CHAT_MODEL_WITH_TOOLS.model(), OPEN_AI_CHAT_MODEL_WITH_TOOLS.model());
    }

    @Override
    protected boolean assertTokenUsage() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO implement
    }
}
