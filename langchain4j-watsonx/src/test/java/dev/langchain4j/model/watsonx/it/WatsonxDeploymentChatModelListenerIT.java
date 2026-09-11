package dev.langchain4j.model.watsonx.it;

import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.watsonx.WatsonxDeploymentChatModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GRANITE_3_3_DEPLOYMENT_ID", matches = ".+")
public class WatsonxDeploymentChatModelListenerIT extends AbstractChatModelListenerIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String URL = System.getenv("WATSONX_URL");
    static final String DEPLOYMENT_ID = System.getenv("WATSONX_GRANITE_3_3_DEPLOYMENT_ID");

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return createChatModel(DEPLOYMENT_ID)
                .listeners(List.of(listener))
                .defaultRequestParameters(ChatRequestParameters.builder()
                        .temperature(temperature())
                        .topP(topP())
                        .maxOutputTokens(maxTokens())
                        .build())
                .build();
    }

    @Override
    protected Double temperature() {
        return 0.0;
    }

    @Override
    protected String modelName() {
        // The deployment id already defines the model to call, so the request never carries a model name
        return null;
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return createChatModel("invalid-deployment-id")
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        // An unknown deployment id makes the service answer with HTTP 404
        return ModelNotFoundException.class;
    }

    private WatsonxDeploymentChatModel.Builder createChatModel(String deploymentId) {
        return WatsonxDeploymentChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .deploymentId(deploymentId)
                .timeout(Duration.ofSeconds(120));
    }
}
