package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Test;

class OllamaEmbeddingModelTest {

    @Test
    void exposes_provider_listeners_and_text_only_content_type() {
        EmbeddingModelListener listener = new EmbeddingModelListener() {};

        OllamaEmbeddingModel model = OllamaEmbeddingModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("all-minilm")
                .listeners(List.of(listener))
                .build();

        assertThat(model.provider()).isEqualTo(ModelProvider.OLLAMA);
        assertThat(model.listeners()).containsExactly(listener);
        assertThat(model.supportedContentTypes()).containsExactly(ContentType.TEXT);
        assertThat(model.supportedParameters()).isEmpty();
    }

    @Test
    void reports_token_usage_from_prompt_eval_count() {
        String responseBody = "{\"model\":\"all-minilm\",\"embeddings\":[[0.1,0.2,0.3]],\"prompt_eval_count\":7}";
        MockHttpClient mock = new MockHttpClient(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(responseBody)
                .build());

        OllamaEmbeddingModel model = OllamaEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mock))
                .baseUrl("http://localhost:11434")
                .modelName("all-minilm")
                .build();

        Response<Embedding> response = model.embed("hello");

        assertThat(response.content().vector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(7);
    }
}
