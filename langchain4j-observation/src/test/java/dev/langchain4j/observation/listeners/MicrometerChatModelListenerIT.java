package dev.langchain4j.observation.listeners;

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
    void should_contain_metrics_no_error() {
        doChatRequest(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"));

        // Only token usage metrics should be present
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(meterRegistry.find(AttributeKeys.TOKEN_USAGE.value()).meter())
                        .isNotNull());

        assertThat(meterRegistry
                .find(AttributeKeys.TOKEN_USAGE.value())
                .tag(AttributeKeys.TOKEN_TYPE.value(), "input")
                .meter())
                .isNotNull();
        assertThat(meterRegistry
                .find(AttributeKeys.TOKEN_USAGE.value())
                .tag(AttributeKeys.TOKEN_TYPE.value(), "output")
                .meter())
                .isNotNull();

        assertThat(meterRegistry
                .get(AttributeKeys.TOKEN_USAGE.value())
                .tag(AttributeKeys.TOKEN_TYPE.value(), "input")
                .counter()
                .count())
                .isGreaterThan(1.0);
        assertThat(meterRegistry
                .get(AttributeKeys.TOKEN_USAGE.value())
                .tag(AttributeKeys.TOKEN_TYPE.value(), "output")
                .counter()
                .count())
                .isGreaterThan(1.0);

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo("gen_ai.client.operation.duration")
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue(KeyValue.of(AttributeKeys.OPERATION_NAME.value(), "chat"))
//                .hasLowCardinalityKeyValue(KeyValue.of(AttributeKeys.SYSTEM.value(), "azure_openai"))
                .hasLowCardinalityKeyValue(KeyValue.of(AttributeKeys.REQUEST_MODEL.value(), "gpt-4o"))
                .hasLowCardinalityKeyValue(KeyValue.of(AttributeKeys.RESPONSE_MODEL.value(), "gpt-4o-2024-11-20"))
                .hasLowCardinalityKeyValue(KeyValue.of("outcome", "SUCCESS"));
    }

    @Test
    void should_contain_metrics_with_error_on_request() {
        doChatRequest("wrongDeploymentName");

        // No token usage metrics on error
        assertThat(meterRegistry.find(AttributeKeys.TOKEN_USAGE.value()).meter())
                .isNull();
        assertThat(meterRegistry
                .find(AttributeKeys.TOKEN_USAGE.value())
                .tag(AttributeKeys.TOKEN_TYPE.value(), "input")
                .meter())
                .isNull();
        assertThat(meterRegistry
                .find(AttributeKeys.TOKEN_USAGE.value())
                .tag(AttributeKeys.TOKEN_TYPE.value(), "output")
                .meter())
                .isNull();
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
