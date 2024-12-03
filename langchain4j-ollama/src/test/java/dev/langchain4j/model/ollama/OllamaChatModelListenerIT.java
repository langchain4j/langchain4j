package dev.langchain4j.model.ollama;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import static dev.langchain4j.model.ollama.OllamaImage.TOOL_MODEL;
import static java.util.Collections.singletonList;

class OllamaChatModelListenerIT extends ChatModelListenerIT {

    @Override
    protected ChatLanguageModel createModel(ChatModelListener listener) {
        return OllamaChatModel.builder()
                .baseUrl(AbstractOllamaToolsLanguageModelInfrastructure.ollamaBaseUrl())
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
        return TOOL_MODEL;
    }

    @Override
    protected ChatLanguageModel createFailingModel(ChatModelListener listener) {
        return OllamaChatModel.builder()
                .baseUrl(AbstractOllamaToolsLanguageModelInfrastructure.ollamaBaseUrl())
                .modelName("banana")
                .maxRetries(0)
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return RuntimeException.class;
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
