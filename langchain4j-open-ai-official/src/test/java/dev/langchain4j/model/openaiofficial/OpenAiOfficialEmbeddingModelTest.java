package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OpenAiOfficialEmbeddingModelTest {

    private static final String RESPONSE = """
            {
              "object": "list",
              "data": [{"object": "embedding", "index": 0, "embedding": [0.1, 0.2]}],
              "model": "text-embedding-ada-002",
              "usage": {"prompt_tokens": 2, "total_tokens": 2}
            }
            """;

    final OpenAiOfficialStubHttpClient httpClient = new OpenAiOfficialStubHttpClient();

    @Test
    void should_not_send_dimensions_when_not_set() {

        // given: text-embedding-ada-002 rejects the "dimensions" parameter
        httpClient.enqueue("embeddings", RESPONSE);
        OpenAiOfficialEmbeddingModel model =
                model().modelName("text-embedding-ada-002").build();

        // when
        model.embed("hello");

        // then
        assertThat(httpClient.requestTo("embeddings").body()).doesNotContain("dimensions");
    }

    @Test
    void should_send_dimensions_when_set() {

        // given
        httpClient.enqueue("embeddings", RESPONSE);
        OpenAiOfficialEmbeddingModel model =
                model().modelName("text-embedding-3-small").dimensions(256).build();

        // when
        model.embed("hello");

        // then
        assertThat(httpClient.requestTo("embeddings").body()).contains("\"dimensions\":256");
    }

    @ParameterizedTest
    @CsvSource({"text-embedding-3-small, 1536", "text-embedding-3-large, 3072", "text-embedding-ada-002, 1536"})
    void should_return_known_dimension_without_calling_the_api(String modelName, int expectedDimension) {

        OpenAiOfficialEmbeddingModel model = model().modelName(modelName).build();

        assertThat(model.dimension()).isEqualTo(expectedDimension);
        assertThat(httpClient.recordedRequests()).isEmpty();
    }

    @Test
    void should_return_configured_dimensions_as_dimension() {

        OpenAiOfficialEmbeddingModel model =
                model().modelName("text-embedding-3-large").dimensions(256).build();

        assertThat(model.dimension()).isEqualTo(256);
        assertThat(httpClient.recordedRequests()).isEmpty();
    }

    private OpenAiOfficialEmbeddingModel.Builder model() {
        return OpenAiOfficialEmbeddingModel.builder()
                .openAIClient(new OpenAIClientImpl(ClientOptions.builder()
                        .apiKey("test-key")
                        .httpClient(httpClient)
                        .build()));
    }
}
