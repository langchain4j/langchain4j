package dev.langchain4j.observation.listener;

import static convention.ChatModelDocumentation.HighCardinalityValues.INPUT_TOKENS;
import static convention.ChatModelDocumentation.HighCardinalityValues.OUTPUT_TOKENS;
import static convention.ChatModelDocumentation.LowCardinalityValues.OPERATION_NAME;
import static convention.ChatModelDocumentation.LowCardinalityValues.OUTCOME;
import static convention.ChatModelDocumentation.LowCardinalityValues.REQUEST_MODEL;
import static convention.ChatModelDocumentation.LowCardinalityValues.RESPONSE_MODEL;
import static convention.ChatModelDocumentation.LowCardinalityValues.TOKEN_TYPE;
import static dev.langchain4j.observation.listener.ObservationChatModelListener.TOKEN_USAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
public class ObservationChatModelListenerIT {

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
        doChatRequest("gpt-4o-mini");

        // Only token usage metrics should be present
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(meterRegistry.find(TOKEN_USAGE).meter()).isNotNull());

        assertThat(meterRegistry
                        .find(TOKEN_USAGE)
                        .tag(TOKEN_TYPE.asString(), "input")
                        .meter())
                .isNotNull();
        assertThat(meterRegistry
                        .find(TOKEN_USAGE)
                        .tag(TOKEN_TYPE.asString(), "output")
                        .meter())
                .isNotNull();

        assertThat(meterRegistry
                        .get(TOKEN_USAGE)
                        .tag(TOKEN_TYPE.asString(), "input")
                        .summary()
                        .totalAmount())
                .isGreaterThan(1);
        assertThat(meterRegistry
                        .get(TOKEN_USAGE)
                        .tag(TOKEN_TYPE.asString(), "output")
                        .summary()
                        .totalAmount())
                .isGreaterThan(1);

        final HistogramSnapshot inputSnapshot = meterRegistry
                .get(TOKEN_USAGE)
                .tag(TOKEN_TYPE.asString(), "input")
                .summary()
                .takeSnapshot();
        assertThat(inputSnapshot).isNotNull();
        assertThat(inputSnapshot.count()).isEqualTo(1L);
        assertThat(inputSnapshot.total()).isGreaterThanOrEqualTo(8.0);
        assertThat(inputSnapshot.percentileValues()).isEmpty();
        assertThat(inputSnapshot.histogramCounts()).isEmpty();

        // Output tokens (145): should appear in le:256 bucket (145 <= 256) but not le:64 (145 > 64)
        final HistogramSnapshot outputHistogram = meterRegistry
                .get(TOKEN_USAGE)
                .tag(TOKEN_TYPE.asString(), "output")
                .summary()
                .takeSnapshot();
        assertThat(outputHistogram).isNotNull();
        assertThat(outputHistogram.count()).isEqualTo(1L);
        assertThat(outputHistogram.total()).isGreaterThanOrEqualTo(7.0);
        assertThat(outputHistogram.percentileValues()).isEmpty();
        assertThat(outputHistogram.histogramCounts()).isEmpty();

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo("gen_ai.client.operation.duration")
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue(KeyValue.of(OPERATION_NAME.asString(), "chat"))
                .hasLowCardinalityKeyValue(KeyValue.of(REQUEST_MODEL.asString(), "gpt-4o-mini"))
                .hasLowCardinalityKeyValue(KeyValue.of(RESPONSE_MODEL.asString(), "gpt-4o-mini-2024-07-18"))
                .hasLowCardinalityKeyValue(KeyValue.of(OUTCOME.asString(), "SUCCESS"))
                .hasHighCardinalityKeyValueWithKey(OUTPUT_TOKENS.asString())
                .hasHighCardinalityKeyValueWithKey(INPUT_TOKENS.asString());
    }

    @Test
    void requestError() {
        try {
            doChatRequest("wrongDeploymentName");
        } catch (ModelNotFoundException expected) {
            // ignored because expected behavior
        }
        // No token usage metrics on error
        assertThat(meterRegistry.find(TOKEN_USAGE).meter()).isNull();
        assertThat(meterRegistry
                        .find(TOKEN_USAGE)
                        .tag(TOKEN_TYPE.asString(), "input")
                        .meter())
                .isNull();
        assertThat(meterRegistry
                        .find(TOKEN_USAGE)
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
                .doesNotHaveLowCardinalityKeyValue(KeyValue.of(RESPONSE_MODEL.asString(), "gpt-4o-mini-2024-11-20"))
                .hasLowCardinalityKeyValue(KeyValue.of(OUTCOME.asString(), "ERROR"));
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

        ChatResponse response = chatModel.chat(chatRequest);
        assertThat(response.metadata()).isNotNull();
    }
}
