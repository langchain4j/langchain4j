package dev.langchain4j.model.deliverance;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveranceEmbeddingModelIT {

    static EmbeddingModel model;

    @BeforeAll
    static void setup() {
        model = DeliveranceEmbeddingModel.builder()
                .modelBuilder(DeliveranceEmbeddingModels.builder(DeliveranceTestUtils.EMBEDDING_MODEL_NAME))
                .build();
    }

    @Test
    void should_embed() {
        Response<Embedding> response = model.embed("hello world");
        assertThat(response.content().vector()).isNotEmpty();
        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_return_correct_dimension() {
        Response<Embedding> response = model.embed("hello world");

        assertThat(model.dimension()).isEqualTo(response.content().dimension());
    }
}
