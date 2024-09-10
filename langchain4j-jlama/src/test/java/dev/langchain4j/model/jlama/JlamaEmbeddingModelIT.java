package dev.langchain4j.model.jlama;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class JlamaEmbeddingModelIT {

    static File tmpDir;

    static EmbeddingModel model;


    @BeforeAll
    static void setup() {
        tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();

        model = JlamaEmbeddingModel.builder()
                .modelName("intfloat/e5-small-v2")
                .modelCachePath(tmpDir.toPath())
                .build();
    }

    @Test
    void should_embed() {

        // given
        String text = "hello world";

        // when
        Response<Embedding> response = model.embed(text);

        // then
        assertThat(response.content().vector()).isNotEmpty();

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_return_correct_dimension() {
        // given
        String text = "hello world";

        // when
        Response<Embedding> response = model.embed(text);

        // then
        assertThat(model.dimension()).isEqualTo(response.content().dimension());
    }
}
