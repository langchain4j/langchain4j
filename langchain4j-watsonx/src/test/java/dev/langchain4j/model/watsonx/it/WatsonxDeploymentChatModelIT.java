package dev.langchain4j.model.watsonx.it;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.langchain4j.model.watsonx.WatsonxDeploymentChatModel;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_DEPLOYMENT_ID", matches = ".+")
public class WatsonxDeploymentChatModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String URL = System.getenv("WATSONX_URL");
    static final String DEPLOYMENT_ID = System.getenv("WATSONX_DEPLOYMENT_ID");

    @Test
    void should_use_deployed_model_with_deployment_id() {
        var chatModel = WatsonxDeploymentChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .deploymentId(DEPLOYMENT_ID)
                .timeout(Duration.ofSeconds(30))
                .build();

        var response = chatModel.chat("Hello");
        assertNotNull(response);
    }
}
