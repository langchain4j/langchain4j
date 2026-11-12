package dev.langchain4j.micrometer.metrics.listeners;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiAttributes;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiMetricName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiOperationName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiTokenType;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.model.embedding.mock.EmbeddingModelMock;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MicrometerMetricsEmbeddingModelListenerTest {

    MicrometerMetricsEmbeddingModelListener listener;
    MeterRegistry meterRegistry;

    /**
     * Shared by the request, response and error contexts of a single lifecycle, the way {@code EmbeddingModel} passes
     * the same map through every callback.
     */
    Map<Object, Object> attributes = new HashMap<>();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        attributes = new HashMap<>();
        listener = new MicrometerMetricsEmbeddingModelListener(meterRegistry);
    }

    @Test
    void should_record_token_usage_with_input_token_type_only_when_response_is_received() {
        listener.onResponse(responseContextWithTokenUsage(new TokenUsage(10, 20)));

        // an embedding call has no output tokens, so only the input type is recorded
        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.EMBEDDINGS.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.INPUT.value())
                        .summary())
                .isNotNull();
        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.OUTPUT.value())
                        .summary())
                .isNull();
    }

    @Test
    void should_record_unknown_provider_name_when_model_provider_is_null() {
        EmbeddingModel modelWithoutProvider = new EmbeddingModelMock().withProvider(null);

        listener.onResponse(EmbeddingModelResponseContext.builder()
                .embeddingRequest(embeddingRequest("text-embedding-3-small"))
                .embeddingResponse(EmbeddingResponse.builder()
                        .embeddings(List.of(Embedding.from(List.of(0.1f, 0.2f))))
                        .modelName("text-embedding-3-small")
                        .tokenUsage(new TokenUsage(10, null))
                        .build())
                .embeddingModel(modelWithoutProvider)
                .attributes(attributes)
                .response(Response.from(List.of(Embedding.from(List.of(0.1f, 0.2f)))))
                .textSegments(List.of(TextSegment.from("hello")))
                .build());

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.PROVIDER_NAME.value(), "unknown")
                        .meter())
                .isNotNull();
    }

    @Test
    void should_record_unknown_model_names_when_model_names_are_null() {
        listener.onResponse(responseContext(null, new TokenUsage(10, null)));

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.REQUEST_MODEL.value(), "unknown")
                        .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(), "unknown")
                        .meter())
                .isNotNull();
    }

    @Test
    void should_record_no_token_metric_and_not_throw_when_token_usage_is_null() {
        listener.onResponse(responseContext("text-embedding-3-small", null));

        assertThatCode(() -> listener.onResponse(responseContext("text-embedding-3-small", null)))
                .doesNotThrowAnyException();
        assertThat(meterRegistry.find(OTelGenAiMetricName.TOKEN_USAGE.value()).summary())
                .isNull();
    }

    @Test
    void should_record_no_token_metric_when_input_token_count_is_null() {
        listener.onResponse(responseContextWithTokenUsage(new TokenUsage(null, null)));

        assertThat(meterRegistry.find(OTelGenAiMetricName.TOKEN_USAGE.value()).summary())
                .isNull();
    }

    @Test
    void should_record_operation_duration_when_the_call_succeeds() {
        listener.onRequest(requestContext());
        listener.onResponse(responseContextWithTokenUsage(new TokenUsage(10, null)));

        Timer timer = meterRegistry
                .find(OTelGenAiMetricName.OPERATION_DURATION.value())
                .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.EMBEDDINGS.value())
                .tag(OTelGenAiAttributes.PROVIDER_NAME.value(), "azure.ai.inference")
                .tag(OTelGenAiAttributes.REQUEST_MODEL.value(), "text-embedding-3-small")
                .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(), "text-embedding-3-small")
                .tag("outcome", "SUCCESS")
                .tag(OTelGenAiAttributes.ERROR_TYPE.value(), "none")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    void should_record_operation_duration_with_error_outcome_when_the_call_fails() {
        listener.onRequest(requestContext());
        listener.onError(errorContext(new IllegalStateException("boom")));

        // a failed call has no response, so the response model is unknown
        Timer timer = meterRegistry
                .find(OTelGenAiMetricName.OPERATION_DURATION.value())
                .tag(OTelGenAiAttributes.REQUEST_MODEL.value(), "text-embedding-3-small")
                .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(), "unknown")
                .tag("outcome", "ERROR")
                .tag(OTelGenAiAttributes.ERROR_TYPE.value(), IllegalStateException.class.getName())
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    void should_record_operation_duration_with_the_same_tag_keys_on_success_and_failure() {
        listener.onRequest(requestContext());
        listener.onError(errorContext(new IllegalStateException("boom")));
        listener.onRequest(requestContext());
        listener.onResponse(responseContextWithTokenUsage(new TokenUsage(10, null)));

        // registries like Prometheus drop meters whose tag keys differ from an existing meter with the same name
        List<Set<String>> tagKeys = meterRegistry.find(OTelGenAiMetricName.OPERATION_DURATION.value()).timers().stream()
                .map(timer -> timer.getId().getTags().stream().map(Tag::getKey).collect(toSet()))
                .toList();

        assertThat(tagKeys).hasSize(2);
        assertThat(tagKeys.get(0)).isEqualTo(tagKeys.get(1));
    }

    @Test
    void should_not_record_operation_duration_when_the_request_was_not_started() {
        listener.onResponse(responseContextWithTokenUsage(new TokenUsage(10, null)));

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.OPERATION_DURATION.value())
                        .meter())
                .isNull();
    }

    @Test
    void should_record_operation_duration_once_per_request() {
        listener.onRequest(requestContext());
        listener.onResponse(responseContextWithTokenUsage(new TokenUsage(10, null)));
        listener.onResponse(responseContextWithTokenUsage(new TokenUsage(10, null)));

        // the start timestamp is consumed by the first response, so the second one has nothing to measure
        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.OPERATION_DURATION.value())
                        .timer()
                        .count())
                .isEqualTo(1L);
    }

    @Test
    void should_still_record_token_usage_when_the_request_was_started() {
        listener.onRequest(requestContext());
        listener.onResponse(responseContextWithTokenUsage(new TokenUsage(10, null)));

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.INPUT.value())
                        .summary())
                .isNotNull();
    }

    private EmbeddingModelRequestContext requestContext() {
        return EmbeddingModelRequestContext.builder()
                .embeddingRequest(embeddingRequest("text-embedding-3-small"))
                .embeddingModel(embeddingModel())
                .attributes(attributes)
                .textSegments(List.of(TextSegment.from("hello")))
                .build();
    }

    private EmbeddingModelErrorContext errorContext(Throwable error) {
        return EmbeddingModelErrorContext.builder()
                .error(error)
                .embeddingRequest(embeddingRequest("text-embedding-3-small"))
                .embeddingModel(embeddingModel())
                .attributes(attributes)
                .textSegments(List.of(TextSegment.from("hello")))
                .build();
    }

    private EmbeddingModelResponseContext responseContextWithTokenUsage(TokenUsage tokenUsage) {
        return responseContext("text-embedding-3-small", tokenUsage);
    }

    private EmbeddingModelResponseContext responseContext(String modelName, TokenUsage tokenUsage) {
        EmbeddingResponse.Builder responseBuilder =
                EmbeddingResponse.builder().embeddings(List.of(Embedding.from(List.of(0.1f, 0.2f))));
        EmbeddingModelResponseContext.Builder contextBuilder = EmbeddingModelResponseContext.builder()
                .embeddingModel(embeddingModel())
                .attributes(attributes)
                .embeddingRequest(embeddingRequest(modelName))
                .response(Response.from(List.of(Embedding.from(List.of(0.1f, 0.2f)))))
                .textSegments(List.of(TextSegment.from("hello")));
        if (modelName != null) {
            responseBuilder.modelName(modelName);
        }
        if (tokenUsage != null) {
            responseBuilder.tokenUsage(tokenUsage);
        }
        return contextBuilder.embeddingResponse(responseBuilder.build()).build();
    }

    private static EmbeddingRequest embeddingRequest(String modelName) {
        EmbeddingRequest.Builder builder = EmbeddingRequest.builder().input("hello");
        if (modelName != null) {
            builder.modelName(modelName);
        }
        return builder.build();
    }

    private static EmbeddingModel embeddingModel() {
        return new EmbeddingModelMock().withProvider(ModelProvider.MICROSOFT_FOUNDRY);
    }
}
