package dev.langchain4j.micrometer.listeners;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.micrometer.conventions.OTelGenAiAttributes;
import dev.langchain4j.micrometer.conventions.OTelGenAiMetricName;
import dev.langchain4j.micrometer.conventions.OTelGenAiTokenType;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerChatModelListenerIT {

    TestObservationRegistry observationRegistry;
    MicrometerChatModelListener listener;
    MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        observationRegistry = TestObservationRegistry.create();
        listener = new MicrometerChatModelListener(meterRegistry, observationRegistry, "azure_openai");
    }

    @Test
    void should_observe_chat_request_and_response() {
        doChatRequest(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"));

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo("gen_ai.client.operation.duration")
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue(KeyValue.of(OTelGenAiAttributes.OPERATION_NAME.value(), "chat"))
                .hasLowCardinalityKeyValue(KeyValue.of(OTelGenAiAttributes.SYSTEM.value(), "azure_openai"))
                .hasLowCardinalityKeyValue(KeyValue.of(OTelGenAiAttributes.REQUEST_MODEL.value(), "gpt-4o"))
                .hasLowCardinalityKeyValue(KeyValue.of(OTelGenAiAttributes.RESPONSE_MODEL.value(), "gpt-4o"));
    }

    @Test
    void should_contain_metrics_no_error() {
        doChatRequest(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"));

        assertThat(meterRegistry.getMeters()).hasSize(3);
        assertThat(meterRegistry.find("langchain4j.chat.model.request").meter()).isNotNull();
        assertThat(meterRegistry.find(OTelGenAiMetricName.TOKEN_USAGE.value()).meter())
                .isNotNull();
        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.INPUT.value())
                        .meter())
                .isNotNull();
        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.OUTPUT.value())
                        .meter())
                .isNotNull();

        assertThat(meterRegistry.get("langchain4j.chat.model.request").counter().count())
                .isEqualTo(1.0);
        assertThat(meterRegistry
                        .get(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.INPUT.value())
                        .counter()
                        .count())
                .isGreaterThan(1.0);
        assertThat(meterRegistry
                        .get(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.OUTPUT.value())
                        .counter()
                        .count())
                .isGreaterThan(1.0);

        //        assertThat(meterRegistry.find("gen_ai.client.operation.duration").timer()).isNotNull();
        //        assertThat(meterRegistry.find("gen_ai.client.operation.duration")
        //                .timer()
        //                .count()).isGreaterThan(0);
    }

    @Test
    void should_contain_metrics_with_error_on_request() {
        doChatRequest("wrongDeploymentName");

        assertThat(meterRegistry.getMeters()).hasSize(2);
        assertThat(meterRegistry.find("langchain4j.chat.model.request").meter()).isNotNull();
        assertThat(meterRegistry.find("langchain4j.chat.model.error").meter()).isNotNull();
        assertThat(meterRegistry.find(OTelGenAiMetricName.TOKEN_USAGE.value()).meter())
                .isNull();
        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.INPUT.value())
                        .meter())
                .isNull();
        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.OUTPUT.value())
                        .meter())
                .isNull();

        assertThat(meterRegistry.get("langchain4j.chat.model.request").counter().count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.get("langchain4j.chat.model.error").counter().count())
                .isEqualTo(1.0);
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
