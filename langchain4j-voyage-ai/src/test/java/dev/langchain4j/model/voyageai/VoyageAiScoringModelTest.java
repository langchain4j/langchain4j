package dev.langchain4j.model.voyageai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Test;

class VoyageAiScoringModelTest {

    private static final List<TextSegment> SEGMENTS =
            List.of(TextSegment.from("first segment"), TextSegment.from("second segment"));

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
