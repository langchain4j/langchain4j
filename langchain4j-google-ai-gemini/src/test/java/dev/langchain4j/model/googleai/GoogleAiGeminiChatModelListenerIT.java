package dev.langchain4j.model.googleai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.AfterEach;

import static java.util.Collections.singletonList;

class GoogleAiGeminiChatModelListenerIT extends ChatModelListenerIT {

    @Override
    protected ChatLanguageModel createModel(ChatModelListener listener) {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxOutputTokens(maxTokens())
                .listeners(singletonList(listener))
                .logRequestsAndResponses(true)
                .build();
    }

    @Override
    protected String modelName() {
        return "gemini-1.5-flash";
    }

    @Override
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected ChatLanguageModel createFailingModel(ChatModelListener listener) {
        return GoogleAiGeminiChatModel.builder()
                .apiKey("banana")
                .modelName(modelName())
                .maxRetries(1)
                .listeners(singletonList(listener))
                .logRequestsAndResponses(true)
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return RuntimeException.class;
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
