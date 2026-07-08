package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAiEmbeddingModelTest {

    @Test
    void should_notify_configured_listener() {
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(embeddingResponse());

        AtomicReference<EmbeddingModelRequestContext> requestContext = new AtomicReference<>();
        AtomicReference<EmbeddingModelResponseContext> responseContext = new AtomicReference<>();
        EmbeddingModelListener listener = new EmbeddingModelListener() {
            @Override
            public void onRequest(EmbeddingModelRequestContext ctx) {
                requestContext.set(ctx);
            }

            @Override
            public void onResponse(EmbeddingModelResponseContext ctx) {
                responseContext.set(ctx);
            }
        };

        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName("text-embedding-3-small")
                .listeners(List.of(listener))
                .build();

        model.embed(EmbeddingRequest.builder().input("hello").build());

        assertThat(requestContext.get()).isNotNull();
        assertThat(requestContext.get().textSegments()).hasSize(1);
        assertThat(requestContext.get().embeddingModel()).isSameAs(model);
        assertThat(responseContext.get()).isNotNull();
        assertThat(responseContext.get().response().content()).hasSize(1);
        // request and response share the same attributes map
        assertThat(responseContext.get().attributes()).isSameAs(requestContext.get().attributes());
    }

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
