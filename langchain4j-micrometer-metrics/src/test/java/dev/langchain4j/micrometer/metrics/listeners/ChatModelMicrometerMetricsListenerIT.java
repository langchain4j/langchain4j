package dev.langchain4j.micrometer.metrics.listeners;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiAttributes;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiMetricName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiTokenType;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatModelMicrometerMetricsListenerIT {

    ChatModelMicrometerMetricsListener listener;
    MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new ChatModelMicrometerMetricsListener(meterRegistry);
    }

    @Test
    void should_contain_metrics_no_error() {
        doChatRequest(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"));

        // Only token usage metrics should be present
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

        // No token usage metrics on error
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
