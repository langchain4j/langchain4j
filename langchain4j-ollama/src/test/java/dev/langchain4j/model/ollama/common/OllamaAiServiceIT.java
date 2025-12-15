package dev.langchain4j.model.ollama.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.common.AbstractAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.ollama.common.OllamaChatModelIT.OLLAMA_CHAT_MODEL_WITH_TOOLS;
import static dev.langchain4j.model.ollama.common.OllamaChatModelIT.OPEN_AI_CHAT_MODEL_WITH_TOOLS;

class OllamaAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(OLLAMA_CHAT_MODEL_WITH_TOOLS, OPEN_AI_CHAT_MODEL_WITH_TOOLS);
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel chatModel) {
        if (chatModel instanceof OpenAiChatModel) {
            return OpenAiTokenUsage.class;
        } else if (chatModel instanceof OllamaChatModel) {
            return TokenUsage.class;
        } else {
            throw new IllegalStateException("Unknown model type: " + chatModel.getClass());
        }
    }
}
