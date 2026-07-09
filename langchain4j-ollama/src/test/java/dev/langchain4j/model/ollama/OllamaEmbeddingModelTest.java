package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
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
}
