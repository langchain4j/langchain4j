package dev.langchain4j.model.ollama;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static java.util.Collections.singletonList;

public class OllamaStreamingChatModelListenerIT extends StreamingChatModelListenerIT {

    @Override
    protected StreamingChatLanguageModel createModel(ChatModelListener listener) {
        return OllamaStreamingChatModel.builder()
            .baseUrl(AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl())
            .modelName(modelName())
            .temperature(temperature())
            .topP(topP())
            .numPredict(maxTokens())
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
            .baseUrl(AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl())
            .modelName("banana")
            .logRequests(true)
            .logResponses(true)
            .listeners(singletonList(listener))
            .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return NullPointerException.class;
    }

    @Override
    protected boolean supportsTools() {
        return false;
    }

    @Override
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertFinishReason() {
        return false;
    }
}
