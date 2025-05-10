package dev.langchain4j.model.ollama.common;

import static dev.langchain4j.model.ollama.common.OllamaStreamingChatModelIT.OLLAMA_CHAT_MODEL_WITH_TOOLS;
import static dev.langchain4j.model.ollama.common.OllamaStreamingChatModelIT.OPEN_AI_CHAT_MODEL_WITH_TOOLS;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import java.util.List;

class OllamaStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(OLLAMA_CHAT_MODEL_WITH_TOOLS, OPEN_AI_CHAT_MODEL_WITH_TOOLS);
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel streamingChatModel) {
        if (streamingChatModel instanceof OpenAiStreamingChatModel) {
            return OpenAiChatResponseMetadata.class;
        } else if (streamingChatModel instanceof OllamaStreamingChatModel) {
            return ChatResponseMetadata.class;
        } else {
            throw new IllegalStateException("Unknown model type: " + streamingChatModel.getClass());
        }
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel streamingChatModel) {
        if (streamingChatModel instanceof OpenAiStreamingChatModel) {
            return OpenAiTokenUsage.class;
        } else if (streamingChatModel instanceof OllamaStreamingChatModel) {
            return TokenUsage.class;
        } else {
            throw new IllegalStateException("Unknown model type: " + streamingChatModel.getClass());
        }
    }
}
