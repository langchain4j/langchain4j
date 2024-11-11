package dev.langchain4j.model.googleai;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;

import static java.util.Collections.singletonList;

class GoogleAiGeminiStreamingChatModelListenerIT extends StreamingChatModelListenerIT {
    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Override
    protected StreamingChatLanguageModel createModel(ChatModelListener listener) {
        return GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
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
    protected StreamingChatLanguageModel createFailingModel(ChatModelListener listener) {
        return GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("banana")
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
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
