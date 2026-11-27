package dev.langchain4j.model.ollama.common;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.OLLAMA_BASE_URL;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaImage.LLAMA_3_1;
import static dev.langchain4j.model.ollama.OllamaImage.OLLAMA_IMAGE;
import static dev.langchain4j.model.ollama.OllamaImage.localOllamaImage;
import static dev.langchain4j.model.ollama.OllamaImage.resolve;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.ollama.LC4jOllamaContainer;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.AfterAll;

class OllamaStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    private static final String MODEL_NAME = LLAMA_3_1;
    private static LC4jOllamaContainer ollama;

    static {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            String localOllamaImage = localOllamaImage(MODEL_NAME);
            ollama = new LC4jOllamaContainer(resolve(OLLAMA_IMAGE, localOllamaImage)).withModel(MODEL_NAME);
            ollama.start();
            ollama.commitToImage(localOllamaImage);
        }
    }

    @AfterAll
    static void stopOllama() {
        if (ollama != null) {
            ollama.stop();
        }
    }

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(
                OllamaStreamingChatModel.builder()
                        .baseUrl(ollamaBaseUrl(ollama))
                        .modelName(MODEL_NAME)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .timeout(ofSeconds(180))
                        .build(),
                OpenAiStreamingChatModel.builder()
                        .baseUrl(ollamaBaseUrl(ollama) + "/v1")
                        .modelName(MODEL_NAME)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .timeout(ofSeconds(180))
                        .build());
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
