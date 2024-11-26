package dev.langchain4j.model.ollama.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.ollama.common.OllamaStreamingChatModelIT.OLLAMA_STREAMING_CHAT_MODEL;
import static dev.langchain4j.model.ollama.common.OllamaStreamingChatModelIT.OPEN_AI_STREAMING_CHAT_MODEL;

class OllamaStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(OLLAMA_STREAMING_CHAT_MODEL, OPEN_AI_STREAMING_CHAT_MODEL);
    }

    @Override
    protected boolean assertTokenUsage() {
        return false; // TODO fix
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO fix
    }
}
