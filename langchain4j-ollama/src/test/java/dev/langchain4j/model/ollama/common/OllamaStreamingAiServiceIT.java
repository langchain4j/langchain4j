package dev.langchain4j.model.ollama.common;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.ollama.common.OllamaStreamingChatModelIT.OLLAMA_CHAT_MODEL_WITH_TOOLS;
import static dev.langchain4j.model.ollama.common.OllamaStreamingChatModelIT.OPEN_AI_CHAT_MODEL_WITH_TOOLS;

class OllamaStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(
                OLLAMA_CHAT_MODEL_WITH_TOOLS,
                OPEN_AI_CHAT_MODEL_WITH_TOOLS
        );
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
