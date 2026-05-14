package dev.langchain4j.model.google.genai;

import static java.util.Collections.singletonList;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GoogleGenAiChatModelListenerIT extends AbstractChatModelListenerIT {

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return GoogleGenAiChatModel.builder()
                .apiKey(System.getenv("GEMINI_API_KEY"))
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxOutputTokens(maxTokens())
                .listeners(singletonList(listener))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected String modelName() {
        return "gemini-2.5-flash";
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return GoogleGenAiChatModel.builder()
                .apiKey("banana")
                .modelName(modelName())
                .maxRetries(0)
                .listeners(singletonList(listener))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return RuntimeException.class; // Usually wrapped or com.google.genai.exception
    }

    @Override
    protected boolean assertResponseId() {
        return false;
    }
}
