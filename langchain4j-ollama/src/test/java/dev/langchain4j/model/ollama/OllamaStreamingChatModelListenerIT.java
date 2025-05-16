package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollama;
import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static java.util.Collections.singletonList;

import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;

public class OllamaStreamingChatModelListenerIT extends AbstractStreamingChatModelListenerIT {

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl(ollama))
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
    protected StreamingChatModel createFailingModel(ChatModelListener listener) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl(ollama))
                .modelName("banana")
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return ModelNotFoundException.class;
    }

    @Override
    protected boolean supportsTools() {
        return false;
    }

    @Override
    protected boolean assertResponseId() {
        return false;
    }
}
