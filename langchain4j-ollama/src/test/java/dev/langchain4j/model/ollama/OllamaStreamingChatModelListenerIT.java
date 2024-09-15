package dev.langchain4j.model.ollama;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static java.util.Collections.singletonList;

public class OllamaStreamingChatModelListenerIT extends StreamingChatModelListenerIT {

    @Override
    protected StreamingChatLanguageModel createModel(ChatModelListener listener) {
        double temperature = 0.7;
        double topP = 1.0;
        int maxTokens = 7;
        return OllamaStreamingChatModel.builder()
                .baseUrl(AbstractOllamaLanguageModelInfrastructure.ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .temperature(temperature)
                .topP(topP)
                .numPredict(maxTokens)
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return TINY_DOLPHIN_MODEL;
    }

    @Override
    protected StreamingChatLanguageModel createFailingModel(ChatModelListener listener) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(AbstractOllamaLanguageModelInfrastructure.ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<?> expectedExceptionClass() {
        return RuntimeException.class;
    }
}
