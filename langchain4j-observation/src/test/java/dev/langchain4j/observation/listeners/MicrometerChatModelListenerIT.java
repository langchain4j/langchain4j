package dev.langchain4j.observation.listeners;

import static dev.langchain4j.observation.listeners.ChatModelDocumentation.HighCardinalityValues.TOKEN_USAGE;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.OPERATION_NAME;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.REQUEST_MODEL;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.RESPONSE_MODEL;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.TOKEN_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MicrometerChatModelListenerIT {

    private TestObservationRegistry observationRegistry;
    private ObservationChatModelListener listener;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        observationRegistry = TestObservationRegistry.create();
        listener = new ObservationChatModelListener(observationRegistry, meterRegistry);
    }

    @Test
    void requestAndResponse_ok() {
        doChatRequest(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"));

        // Only token usage metrics should be present
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(meterRegistry.find(TOKEN_USAGE.asString()).meter())
                        .isNotNull());

        assertThat(meterRegistry
                .find(TOKEN_USAGE.asString())
                .tag(TOKEN_TYPE.asString(), "input")
                .meter())
                .isNotNull();
        assertThat(meterRegistry
                .find(TOKEN_USAGE.asString())
                .tag(TOKEN_TYPE.asString(), "output")
                .meter())
                .isNotNull();

        assertThat(meterRegistry
                .get(TOKEN_USAGE.asString())
                .tag(TOKEN_TYPE.asString(), "input")
                .counter()
                .count())
                .isGreaterThan(1.0);
        assertThat(meterRegistry
                .get(TOKEN_USAGE.asString())
                .tag(TOKEN_TYPE.asString(), "output")
                .counter()
                .count())
                .isGreaterThan(1.0);

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo("gen_ai.client.operation.duration")
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue(KeyValue.of(OPERATION_NAME.asString(), "chat"))
                .hasLowCardinalityKeyValue(KeyValue.of(REQUEST_MODEL.asString(), "gpt-4o"))
                .hasLowCardinalityKeyValue(KeyValue.of(RESPONSE_MODEL.asString(), "gpt-4o-2024-11-20"))
                .hasLowCardinalityKeyValue(KeyValue.of("outcome", "SUCCESS"))
                .hasHighCardinalityKeyValueWithKey("output_tokens")
                .hasHighCardinalityKeyValueWithKey("input_tokens");
    }

    @Test
    void requestError() {
        doChatRequest("wrongDeploymentName");

        // No token usage metrics on error
        assertThat(meterRegistry.find(TOKEN_USAGE.asString()).meter())
                .isNull();
        assertThat(meterRegistry
                .find(TOKEN_USAGE.asString())
                .tag(TOKEN_TYPE.asString(), "input")
                .meter())
                .isNull();
        assertThat(meterRegistry
                .find(TOKEN_USAGE.asString())
                .tag(TOKEN_TYPE.asString(), "output")
                .meter())
                .isNull();

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo("gen_ai.client.operation.duration")
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue(KeyValue.of(OPERATION_NAME.asString(), "chat"))
                .hasLowCardinalityKeyValue(KeyValue.of(REQUEST_MODEL.asString(), "wrongDeploymentName"))
                .doesNotHaveLowCardinalityKeyValue(KeyValue.of(RESPONSE_MODEL.asString(), "gpt-4o-2024-11-20"))
                .hasLowCardinalityKeyValue(KeyValue.of("outcome", "ERROR"));
    }

    private void doChatRequest(String deploymentName) {
        AzureOpenAiChatModel chatModel = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .temperature(0.7)
                .topP(1.0)
                .maxTokens(7)
                .logRequestsAndResponses(true)
                .listeners(List.of(listener))
                .build();

        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from("Hi")).build();

        try {
            ChatResponse response = chatModel.chat(chatRequest);
            assertThat(response.metadata()).isNotNull();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
