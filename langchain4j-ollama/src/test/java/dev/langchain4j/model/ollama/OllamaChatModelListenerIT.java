package dev.langchain4j.model.ollama;

import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollama;
import static dev.langchain4j.model.ollama.OllamaImage.LLAMA_3_1;
import static java.util.Collections.singletonList;

class OllamaChatModelListenerIT extends ChatModelListenerIT {

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return OllamaChatModel.builder()
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
        return LLAMA_3_1;
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return OllamaChatModel.builder()
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
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertFinishReason() {
        return false;
    }
}
