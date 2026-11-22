package dev.langchain4j.model.voyageai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.output.Response;
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

class VoyageAiScoringModelTest {

    private static final List<TextSegment> SEGMENTS =
            List.of(TextSegment.from("first segment"), TextSegment.from("second segment"));

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
    void should_return_scores_in_input_segment_order() {
        // given
        MockHttpClient mockHttpClient = respondingWith("""
                {
                  "object": "list",
                  "data": [
                    {
                      "object": "rerank_result",
                      "relevance_score": 0.9,
                      "index": 1
                    },
                    {
                      "object": "rerank_result",
                      "relevance_score": 0.1,
                      "index": 0
                    }
                  ],
                  "model": "rerank-2",
                  "usage": {
                    "total_tokens": 10
                  }
                }
                """);
        VoyageAiScoringModel model = model(mockHttpClient, null);

        // when
        Response<List<Double>> response = model.scoreAll(SEGMENTS, "query");

        // then
        assertThat(response.content()).containsExactly(0.1, 0.9);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(10);
    }

    @Test
    void should_fail_before_request_when_top_k_would_truncate_scores() {
        // given
        MockHttpClient mockHttpClient = new MockHttpClient();
        VoyageAiScoringModel model = model(mockHttpClient, 1);

        // when / then
        assertThatThrownBy(() -> model.scoreAll(SEGMENTS, "query"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'topK' (1) must be greater than or equal to the number of segments (2)")
                .hasMessageContaining("ReRankingContentAggregator.builder().maxResults");
        assertThat(mockHttpClient.requests()).isEmpty();
    }

    @Test
    void should_fail_when_response_does_not_contain_one_score_per_segment() {
        // given
        MockHttpClient mockHttpClient = respondingWith("""
                {
                  "object": "list",
                  "data": [
                    {
                      "object": "rerank_result",
                      "relevance_score": 0.9,
                      "index": 1
                    }
                  ],
                  "model": "rerank-2",
                  "usage": {
                    "total_tokens": 10
                  }
                }
                """);
        VoyageAiScoringModel model = model(mockHttpClient, null);

        // when / then
        assertThatThrownBy(() -> model.scoreAll(SEGMENTS, "query"))
                .isInstanceOf(InternalServerException.class)
                .hasMessage("Re-ranking failed: expected 2 scores, but got 1");
    }

    @Test
    void should_fail_when_response_contains_duplicate_index() {
        // given
        MockHttpClient mockHttpClient = respondingWith("""
                {
                  "object": "list",
                  "data": [
                    {
                      "object": "rerank_result",
                      "relevance_score": 0.9,
                      "index": 1
                    },
                    {
                      "object": "rerank_result",
                      "relevance_score": 0.1,
                      "index": 1
                    }
                  ],
                  "model": "rerank-2",
                  "usage": {
                    "total_tokens": 10
                  }
                }
                """);
        VoyageAiScoringModel model = model(mockHttpClient, null);

        // when / then
        assertThatThrownBy(() -> model.scoreAll(SEGMENTS, "query"))
                .isInstanceOf(InternalServerException.class)
                .hasMessage("Re-ranking failed: got a duplicate document index: 1");
    }

    @Test
    void should_fail_when_response_contains_out_of_range_index() {
        // given
        MockHttpClient mockHttpClient = respondingWith("""
                {
                  "object": "list",
                  "data": [
                    {
                      "object": "rerank_result",
                      "relevance_score": 0.9,
                      "index": 0
                    },
                    {
                      "object": "rerank_result",
                      "relevance_score": 0.1,
                      "index": 7
                    }
                  ],
                  "model": "rerank-2",
                  "usage": {
                    "total_tokens": 10
                  }
                }
                """);
        VoyageAiScoringModel model = model(mockHttpClient, null);

        // when / then
        assertThatThrownBy(() -> model.scoreAll(SEGMENTS, "query"))
                .isInstanceOf(InternalServerException.class)
                .hasMessage("Re-ranking failed: got an out-of-range document index: 7");
    }

    @Test
    void should_notify_listeners_on_request_and_response() {
        // given
        VoyageAiScoringModel model = modelWithListener(respondingWith("""
                {"object": "list", "data": [{"index": 1, "relevance_score": 0.9}, {"index": 0, "relevance_score": 0.1}], "model": "rerank-2", "usage": {"total_tokens": 10}}
                """));

        // when
        model.scoreAll(SEGMENTS, "query");

        // then
        assertThat(contexts).hasSize(2);
        ScoringModelRequestContext requestContext = (ScoringModelRequestContext) contexts.get(0);
        assertThat(requestContext.scoringRequest().documents()).containsExactly("first segment", "second segment");
        assertThat(requestContext.scoringRequest().query()).isEqualTo("query");
        assertThat(requestContext.scoringRequest().modelName()).isEqualTo("rerank-2");
        assertThat(requestContext.modelProvider()).isEqualTo(ModelProvider.VOYAGE_AI);

        ScoringModelResponseContext responseContext = (ScoringModelResponseContext) contexts.get(1);
        assertThat(responseContext.scoringResponse().scores()).containsExactly(0.1, 0.9);
        assertThat(responseContext.scoringResponse().modelName()).isEqualTo("rerank-2");
        assertThat(responseContext.scoringResponse().tokenUsage().totalTokenCount()).isEqualTo(10);
        assertThat(responseContext.attributes()).isSameAs(requestContext.attributes());
    }

    @Test
    void should_notify_listeners_on_error() {
        // given
        VoyageAiScoringModel model = modelWithListener(MockHttpClient.thatAlwaysFails(new RuntimeException("boom")));

        // when
        Throwable thrown = catchThrowable(() -> model.scoreAll(SEGMENTS, "query"));

        // then
        assertThat(thrown).hasMessageContaining("boom");
        assertThat(contexts).hasSize(2);
        ScoringModelRequestContext requestContext = (ScoringModelRequestContext) contexts.get(0);
        ScoringModelErrorContext errorContext = (ScoringModelErrorContext) contexts.get(1);
        assertThat(errorContext.error()).isSameAs(thrown);
        assertThat(errorContext.scoringRequest().documents()).containsExactly("first segment", "second segment");
        assertThat(errorContext.modelProvider()).isEqualTo(ModelProvider.VOYAGE_AI);
        assertThat(errorContext.attributes()).isSameAs(requestContext.attributes());
    }

    @Test
    void score_should_send_the_per_call_model_name() {
        // given
        MockHttpClient mockHttpClient = respondingWith("""
                {"object": "list", "data": [{"index": 0, "relevance_score": 0.9}], "model": "per-call-model", "usage": {"total_tokens": 1}}
                """);
        VoyageAiScoringModel model = modelWithListener(mockHttpClient);

        // when
        ScoringResponse response = model.score(ScoringRequest.builder()
                .documents(List.of("first segment"))
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
    void response_should_have_the_model_name_reported_by_voyage_ai() {
        // given
        VoyageAiScoringModel model = modelWithListener(respondingWith("""
                {"object": "list", "data": [{"index": 0, "relevance_score": 0.9}], "model": "rerank-2-2025", "usage": {"total_tokens": 1}}
                """));

        // when
        ScoringResponse response = model.score(
                ScoringRequest.builder().documents(List.of("first segment")).query("query").build());

        // then
        assertThat(response.modelName()).isEqualTo("rerank-2-2025");
        assertThat(((ScoringModelResponseContext) contexts.get(1)).scoringResponse().modelName())
                .isEqualTo("rerank-2-2025");
    }

    @Test
    void scoreAll_should_reject_null_segments_before_calling_the_api() {

        // given
        MockHttpClient mockHttpClient = new MockHttpClient();
        VoyageAiScoringModel model = modelWithListener(mockHttpClient);

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
        VoyageAiScoringModel model = modelWithListener(mockHttpClient);

        // when / then
        assertThatThrownBy(() -> model.scoreAll(SEGMENTS, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(mockHttpClient.requests()).isEmpty();
    }

    private VoyageAiScoringModel modelWithListener(MockHttpClient mockHttpClient) {
        return VoyageAiScoringModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("https://api.voyageai.com/v1")
                .apiKey("test-api-key")
                .modelName("rerank-2")
                .maxRetries(0)
                .listeners(listener)
                .build();
    }

    @SuppressWarnings({"deprecation", "removal"}) // 'topK' is deprecated, but its guard still needs to be tested
    private static VoyageAiScoringModel model(MockHttpClient mockHttpClient, Integer topK) {
        return VoyageAiScoringModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("https://api.voyageai.com/v1")
                .apiKey("test-api-key")
                .modelName("rerank-2")
                .topK(topK)
                .maxRetries(0)
                .build();
    }

    private static MockHttpClient respondingWith(String body) {
        SuccessfulHttpResponse response =
                SuccessfulHttpResponse.builder().statusCode(200).body(body).build();
        return MockHttpClient.thatAlwaysResponds(response);
    }
}
