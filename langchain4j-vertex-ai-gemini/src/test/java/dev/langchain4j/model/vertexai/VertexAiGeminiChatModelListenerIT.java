package dev.langchain4j.model.vertexai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.AfterEach;

import static java.util.Collections.singletonList;

class VertexAiGeminiChatModelListenerIT extends ChatModelListenerIT {

    @Override
    protected ChatLanguageModel createModel(ChatModelListener listener) {
        return VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(modelName())
                .temperature(temperature().floatValue())
                .topP(topP().floatValue())
                .maxOutputTokens(maxTokens())
                .listeners(singletonList(listener))
                .logRequests(true)
                .logResponses(true)
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
        return VertexAiGeminiChatModel.builder()
                .project("GCP_PROJECT_ID")
                .location("GCP_LOCATION")
//                .project(System.getenv("GCP_PROJECT_ID"))
//                .location(System.getenv("GCP_LOCATION"))
                .modelName("banana")
                .listeners(singletonList(listener))
                .logRequests(true)
                .logResponses(true)
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
