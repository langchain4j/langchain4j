package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiEmbeddingModelTest {

    @Test
    void should_send_custom_parameters() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(embeddingResponse());

        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName("nvidia/nv-embedqa-e5-v5")
                .customParameters(Map.<String, Object>of("input_type", "passage"))
                .build();

        // when
        model.embed("hello");

        // then
        assertThat(mockHttpClient.request().body()).isEqualToIgnoringWhitespace("""
                {
                  "model": "nvidia/nv-embedqa-e5-v5",
                  "input": [
                    "hello"
                  ],
                  "input_type": "passage"
                }
                """);
    }

    @Test
    void should_not_send_custom_parameters_when_absent() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(embeddingResponse());

        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName("text-embedding-3-small")
                .build();

        // when
        model.embed("hello");

        // then
        assertThat(mockHttpClient.request().body()).isEqualToIgnoringWhitespace("""
                {
                  "model": "text-embedding-3-small",
                  "input": [
                    "hello"
                  ]
                }
                """);
    }

    @Test
    void should_not_be_affected_when_custom_parameters_map_is_modified_after_build() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(embeddingResponse());
        Map<String, Object> customParameters = new HashMap<>();
        customParameters.put("input_type", "passage");

        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName("nvidia/nv-embedqa-e5-v5")
                .customParameters(customParameters)
                .build();

        customParameters.put("input_type", "query");
        customParameters.put("truncate", "NONE");

        // when
        model.embed("hello");

        // then
        assertThat(mockHttpClient.request().body()).isEqualToIgnoringWhitespace("""
                {
                  "model": "nvidia/nv-embedqa-e5-v5",
                  "input": [
                    "hello"
                  ],
                  "input_type": "passage"
                }
                """);
    }

    private static SuccessfulHttpResponse embeddingResponse() {
        return SuccessfulHttpResponse.builder().statusCode(200).body("""
                        {
                          "model": "text-embedding-3-small",
                          "data": [
                            {
                              "index": 0,
                              "embedding": [0.1, 0.2]
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 1,
                            "total_tokens": 1
                          }
                        }
                        """).build();
    }
}
