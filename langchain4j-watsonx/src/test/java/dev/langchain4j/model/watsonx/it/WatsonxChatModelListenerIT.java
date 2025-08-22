package dev.langchain4j.model.watsonx.it;

import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class WatsonxChatModelListenerIT extends AbstractChatModelListenerIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return createChatModel("meta-llama/llama-4-maverick-17b-128e-instruct-fp8")
                .listeners(List.of(listener))
                .defaultRequestParameters(ChatRequestParameters.builder()
                        .modelName(modelName())
                        .temperature(temperature())
                        .topP(topP())
                        .maxOutputTokens(maxTokens())
                        .build())
                .build();
    }

    @Override
    protected String modelName() {
        return "meta-llama/llama-4-maverick-17b-128e-instruct-fp8";
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return createChatModel("invalid-model").listeners(List.of(listener)).build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return ModelNotFoundException.class;
    }

    private WatsonxChatModel.Builder createChatModel(String model) {
        return WatsonxChatModel.builder()
                .url(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName(model)
                .logRequests(true)
                .logResponses(true)
                .timeLimit(Duration.ofSeconds(30));
    }
}
