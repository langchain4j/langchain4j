package dev.langchain4j.model.watsonx.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.watsonx.WatsonxDeploymentStreamingChatModel;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_DEPLOYMENT_ID", matches = ".+")
public class WatsonxDeploymentStreamingChatModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String URL = System.getenv("WATSONX_URL");
    static final String DEPLOYMENT_ID = System.getenv("WATSONX_DEPLOYMENT_ID");

    @Test
    void should_use_deployed_model_with_deployment_id() {
        var chatModel = WatsonxDeploymentStreamingChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .deploymentId(DEPLOYMENT_ID)
                .timeout(Duration.ofSeconds(30))
                .logRequests(true)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChatResponse> chatResponse = new AtomicReference<ChatResponse>(null);

        chatModel.chat("Hello", new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                latch.countDown();
                chatResponse.set(completeResponse);
            }

            @Override
            public void onError(Throwable error) {}
        });

        assertDoesNotThrow(() -> latch.await(5, TimeUnit.SECONDS));
        assertNotNull(chatResponse.get().aiMessage().text());
    }
}
