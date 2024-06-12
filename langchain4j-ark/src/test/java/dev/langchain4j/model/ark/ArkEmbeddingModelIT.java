package dev.langchain4j.model.ark;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class ArkEmbeddingModelIT {

    EmbeddingModel model = ArkEmbeddingModel.builder()
            .apiKey(System.getenv("ARK_API_KEY"))
            .model(System.getenv("ARK_EMBEDDING_ENDPOINT_ID"))
            .build();

    @Test
    void should_embed_single_text() {

        // given
        String text = "hello world";

        // when
        Response<Embedding> response = model.embed(text);
        System.out.println(response);

        // then
        assertThat(response.content().vector()).hasSize(2048);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(0);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(0);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_multiple_segments() {

        // given
        List<TextSegment> segments = asList(
                TextSegment.from("hello"),
                TextSegment.from("world")
        );

        // when
        Response<List<Embedding>> response = model.embedAll(segments);
        System.out.println(response);

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).dimension()).isEqualTo(2048);
        assertThat(response.content().get(1).dimension()).isEqualTo(2048);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(0);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(0);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_text_with_embedding_shortening() {

        // given
        int dimensions = 2048;// Doubao-embedding support 2048 1024 512, but SDK V3 not support yet

        EmbeddingModel model = ArkEmbeddingModel.builder()
                .apiKey(System.getenv("ARK_API_KEY"))
                .model(System.getenv("ARK_EMBEDDING_ENDPOINT_ID"))
                .dimensions(dimensions)
                .build();

        String text = "hello world";

        // when
        Response<Embedding> response = model.embed(text);
        System.out.println(response);

        // then
        assertThat(response.content().dimension()).isEqualTo(dimensions);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(0);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(0);

        assertThat(response.finishReason()).isNull();
    }
}