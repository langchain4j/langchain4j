package dev.langchain4j.observation.listeners;

import static dev.langchain4j.observation.listeners.ChatModelDocumentation.HighCardinalityValues.TOKEN_USAGE;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.OPERATION_NAME;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.PROVIDER_NAME;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.REQUEST_MODEL;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.RESPONSE_MODEL;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.TOKEN_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.CountAtBucket;
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
                .summary()
                .totalAmount())
                .isGreaterThan(1);
        assertThat(meterRegistry
                .get(TOKEN_USAGE.asString())
                .tag(TOKEN_TYPE.asString(), "output")
                .summary()
                .totalAmount())
                .isGreaterThan(1);

        DistributionSummary inputSummary = meterRegistry
                .get(TOKEN_USAGE.asString())
                .tag(TOKEN_TYPE.asString(), "input")
                .summary();
        CountAtBucket[] inputBuckets = inputSummary.takeSnapshot().histogramCounts();
        assertThat(inputBuckets).isNotEmpty();
        assertThat(countAtBucket(inputBuckets, 16)).isEqualTo(1);
        assertThat(countAtBucket(inputBuckets, 64)).isEqualTo(1);

        // Output tokens (145): should appear in le:256 bucket (145 <= 256) but not le:64 (145 > 64)
        DistributionSummary outputSummary = meterRegistry
                .get(TOKEN_USAGE.asString())
                .tag(TOKEN_TYPE.asString(), "output")
                .summary();
        CountAtBucket[] outputBuckets = outputSummary.takeSnapshot().histogramCounts();
        assertThat(outputBuckets).isNotEmpty();
        assertThat(countAtBucket(outputBuckets, 64)).isEqualTo(1);
        assertThat(countAtBucket(outputBuckets, 256)).isEqualTo(1);

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

    private ChatModelResponseContext createResponseContext(ModelProvider modelProvider) {
        return createResponseContext(modelProvider, "gpt-4o", "gpt-4o");
    }

    private ChatModelResponseContext createResponseContext(
            ModelProvider modelProvider, String requestModelName, String responseModelName) {
        return createResponseContext(modelProvider, requestModelName, responseModelName, 10, 20);
    }

    private ChatModelResponseContext createResponseContext(
            ModelProvider modelProvider,
            String requestModelName,
            String responseModelName,
            int inputTokens,
            int outputTokens) {
        ChatResponse.Builder responseBuilder = ChatResponse.builder()
                .aiMessage(new AiMessage("Hello"))
                .tokenUsage(new TokenUsage(inputTokens, outputTokens));
        if (responseModelName != null) {
            responseBuilder.modelName(responseModelName);
        }

        ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(UserMessage.from("Hi"));
        if (requestModelName != null) {
            requestBuilder.modelName(requestModelName);
        }

        return new ChatModelResponseContext(
                responseBuilder.build(), requestBuilder.build(), modelProvider, new HashMap<>());
    }

    private double countAtBucket(CountAtBucket[] buckets, double boundary) {
        return Arrays.stream(buckets)
                .filter(b -> b.bucket() == boundary)
                .findFirst()
                .map(CountAtBucket::count)
                .orElse(0.0);
    }
}
