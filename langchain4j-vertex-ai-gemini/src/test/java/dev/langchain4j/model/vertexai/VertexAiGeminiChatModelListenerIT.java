package dev.langchain4j.model.vertexai;

import static java.util.Collections.singletonList;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class VertexAiGeminiChatModelListenerIT extends AbstractChatModelListenerIT {

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
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
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("banana")
                .maxRetries(0)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return com.google.api.gax.rpc.NotFoundException.class;
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
