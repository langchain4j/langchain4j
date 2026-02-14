package dev.langchain4j.micrometer.metrics.listeners;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiAttributes;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiMetricName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiTokenType;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MicrometerMetricsChatModelListenerTest {

    MicrometerMetricsChatModelListener listener;
    MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new MicrometerMetricsChatModelListener(meterRegistry);
    }

    @Test
    void should_record_provider_name_when_model_provider_is_present() {
        ChatModelResponseContext responseContext = createResponseContext(ModelProvider.MICROSOFT_FOUNDRY);

        listener.onResponse(responseContext);

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.PROVIDER_NAME.value(), "azure.ai.inference")
                        .meter())
                .isNotNull();
    }

    @Test
    void should_record_unknown_provider_name_when_model_provider_is_null() {
        ChatModelResponseContext responseContext = createResponseContext(null);

        listener.onResponse(responseContext);

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.PROVIDER_NAME.value(), "unknown")
                        .meter())
                .isNotNull();
    }

    @Test
    void should_populate_histogram_buckets_with_recorded_token_counts() {
        ChatModelResponseContext responseContext =
                createResponseContext(ModelProvider.MICROSOFT_FOUNDRY, "gpt-4o", "gpt-4o", 18, 145);

        listener.onResponse(responseContext);

        // Input tokens (18): should appear in le:64 bucket (18 <= 64) but not le:16 (18 > 16)
        DistributionSummary inputSummary = meterRegistry
                .get(OTelGenAiMetricName.TOKEN_USAGE.value())
                .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.INPUT.value())
                .summary();
        CountAtBucket[] inputBuckets = inputSummary.takeSnapshot().histogramCounts();
        assertThat(inputBuckets).isNotEmpty();
        assertThat(countAtBucket(inputBuckets, 16)).isEqualTo(0);
        assertThat(countAtBucket(inputBuckets, 64)).isEqualTo(1);

        // Output tokens (145): should appear in le:256 bucket (145 <= 256) but not le:64 (145 > 64)
        DistributionSummary outputSummary = meterRegistry
                .get(OTelGenAiMetricName.TOKEN_USAGE.value())
                .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.OUTPUT.value())
                .summary();
        CountAtBucket[] outputBuckets = outputSummary.takeSnapshot().histogramCounts();
        assertThat(outputBuckets).isNotEmpty();
        assertThat(countAtBucket(outputBuckets, 64)).isEqualTo(0);
        assertThat(countAtBucket(outputBuckets, 256)).isEqualTo(1);
    }

    @Test
    void should_record_unknown_model_names_when_model_names_are_null() {
        ChatModelResponseContext responseContext = createResponseContext(ModelProvider.MICROSOFT_FOUNDRY, null, null);

        listener.onResponse(responseContext);

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.REQUEST_MODEL.value(), "unknown")
                        .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(), "unknown")
                        .meter())
                .isNotNull();
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
