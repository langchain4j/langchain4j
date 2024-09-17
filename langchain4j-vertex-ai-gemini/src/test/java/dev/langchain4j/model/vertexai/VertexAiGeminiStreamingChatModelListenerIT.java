package dev.langchain4j.model.vertexai;

import com.google.api.gax.rpc.NotFoundException;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import static java.util.Collections.singletonList;

public class VertexAiGeminiStreamingChatModelListenerIT extends StreamingChatModelListenerIT {
    @Override
    protected StreamingChatLanguageModel createModel(ChatModelListener listener) {
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
    protected StreamingChatLanguageModel createFailingModel(ChatModelListener listener) {
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
}
