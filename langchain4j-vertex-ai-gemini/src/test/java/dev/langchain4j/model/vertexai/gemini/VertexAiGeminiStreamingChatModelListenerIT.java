package dev.langchain4j.model.vertexai.gemini;

import com.google.api.gax.rpc.NotFoundException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.AfterEach;

import static java.util.Collections.singletonList;

class VertexAiGeminiStreamingChatModelListenerIT extends AbstractStreamingChatModelListenerIT {

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener) {
        return VertexAiGeminiStreamingChatModel.builder()
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
    protected StreamingChatModel createFailingModel(ChatModelListener listener) {
        return VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("banana")
                .listeners(singletonList(listener))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return NotFoundException.class;
    }


    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
