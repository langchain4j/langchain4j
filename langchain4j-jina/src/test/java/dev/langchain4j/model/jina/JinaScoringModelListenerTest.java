package dev.langchain4j.model.jina;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.listener.ScoringModelErrorContext;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelRequestContext;
import dev.langchain4j.model.scoring.listener.ScoringModelResponseContext;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.request.ScoringRequestParameters;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JinaScoringModelListenerTest {

    private final List<Object> contexts = new ArrayList<>();

    private final ScoringModelListener listener = new ScoringModelListener() {
        @Override
        public void onRequest(ScoringModelRequestContext requestContext) {
            contexts.add(requestContext);
        }

        @Override
        public void onResponse(ScoringModelResponseContext responseContext) {
            contexts.add(responseContext);
        }

        @Override
        public void onError(ScoringModelErrorContext errorContext) {
            contexts.add(errorContext);
        }
    };

    @Test
    void should_notify_listeners_on_request_and_response() {

        // given
        ScoringModel model = model(MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body("""
                        {"model": "test-model", "results": [{"index": 1, "relevance_score": 0.9}, {"index": 0, "relevance_score": 0.1}], "usage": {"prompt_tokens": 10, "total_tokens": 10}}
                        """)
                .build()));

        // when
        model.scoreAll(List.of(TextSegment.from("first"), TextSegment.from("second")), "query");

        // then
        assertThat(contexts).hasSize(2);
        ScoringModelRequestContext requestContext = (ScoringModelRequestContext) contexts.get(0);
        assertThat(requestContext.scoringRequest().documents()).containsExactly("first", "second");
        assertThat(requestContext.scoringRequest().query()).isEqualTo("query");
        assertThat(requestContext.scoringRequest().modelName()).isEqualTo("test-model");
        assertThat(requestContext.modelProvider()).isEqualTo(ModelProvider.JINA);

        ScoringModelResponseContext responseContext = (ScoringModelResponseContext) contexts.get(1);
        assertThat(responseContext.scoringResponse().scores()).containsExactly(0.1, 0.9);
        assertThat(responseContext.scoringResponse().modelName()).isEqualTo("test-model");
        assertThat(responseContext.scoringResponse().tokenUsage().inputTokenCount()).isEqualTo(10);
        assertThat(responseContext.attributes()).isSameAs(requestContext.attributes());
    }

    @Test
    void should_notify_listeners_on_error() {

        // given
        ScoringModel model = model(MockHttpClient.thatAlwaysFails(new RuntimeException("boom")));

        // when
        Throwable thrown = catchThrowable(() -> model.scoreAll(List.of(TextSegment.from("first")), "query"));

        // then
        assertThat(thrown).hasMessageContaining("boom");
        assertThat(contexts).hasSize(2);
        ScoringModelRequestContext requestContext = (ScoringModelRequestContext) contexts.get(0);
        ScoringModelErrorContext errorContext = (ScoringModelErrorContext) contexts.get(1);
        assertThat(errorContext.error()).isSameAs(thrown);
        assertThat(errorContext.scoringRequest().documents()).containsExactly("first");
        assertThat(errorContext.modelProvider()).isEqualTo(ModelProvider.JINA);
        assertThat(errorContext.attributes()).isSameAs(requestContext.attributes());
    }

    @Test
    void scoreAll_should_reject_null_segments_before_calling_the_api() {

        // given
        MockHttpClient mockHttpClient = new MockHttpClient();
        ScoringModel model = model(mockHttpClient);

        // when-then
        assertThatThrownBy(() -> model.scoreAll(null, "query"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("segments");
        assertThat(mockHttpClient.requests()).isEmpty();
    }

    @Test
    void scoreAll_should_reject_a_null_query_before_calling_the_api() {

        // given
        MockHttpClient mockHttpClient = new MockHttpClient();
        ScoringModel model = model(mockHttpClient);

        // when-then
        assertThatThrownBy(() -> model.scoreAll(List.of(TextSegment.from("first")), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(mockHttpClient.requests()).isEmpty();
    }

    @Test
    void score_should_send_the_per_call_model_name() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body("""
                        {"model": "per-call-model", "results": [{"index": 0, "relevance_score": 0.9}], "usage": {"prompt_tokens": 1, "total_tokens": 1}}
                        """)
                .build());
        ScoringModel model = model(mockHttpClient);

        // when
        ScoringResponse response = model.score(ScoringRequest.builder()
                .documents(List.of("first"))
                .query("query")
                .parameters(ScoringRequestParameters.builder().modelName("per-call-model").build())
                .build());

        // then
        assertThat(response.scores()).containsExactly(0.9);
        assertThat(response.modelName()).isEqualTo("per-call-model");
        assertThat(mockHttpClient.request().body()).containsPattern("\"model\"\\s*:\\s*\"per-call-model\"");
        assertThat(((ScoringModelRequestContext) contexts.get(0)).scoringRequest().modelName())
                .isEqualTo("per-call-model");
    }

    @Test
    void response_should_have_the_model_name_reported_by_jina() {

        // given
        ScoringModel model = model(MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body("""
                        {"model": "test-model-v2", "results": [{"index": 0, "relevance_score": 0.9}], "usage": {"prompt_tokens": 1, "total_tokens": 1}}
                        """)
                .build()));

        // when
        ScoringResponse response = model.score(
                ScoringRequest.builder().documents(List.of("first")).query("query").build());

        // then
        assertThat(response.modelName()).isEqualTo("test-model-v2");
        assertThat(((ScoringModelResponseContext) contexts.get(1)).scoringResponse().modelName())
                .isEqualTo("test-model-v2");
    }

    private ScoringModel model(MockHttpClient mockHttpClient) {
        return JinaScoringModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("test-model")
                .maxRetries(0)
                .listeners(List.of(listener))
                .build();
    }
}
