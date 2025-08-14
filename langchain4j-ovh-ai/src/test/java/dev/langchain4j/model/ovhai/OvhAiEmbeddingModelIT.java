package dev.langchain4j.model.ovhai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OVHAI_AI_API_KEY", matches = ".+")
class OvhAiEmbeddingModelIT {

    @Test
    void should_embed_one_segment_with_bge_e5_model() {

        EmbeddingModel model = OvhAiEmbeddingModel.builder()
                .baseUrl("https://bge-base-en-v1-5.endpoints.kepler.ai.cloud.ovh.net")
                .apiKey(System.getenv("OVHAI_AI_API_KEY"))
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        // given
        TextSegment textSegment = TextSegment.from("Embed this sentence.");

        // when
        Response<Embedding> response = model.embed(textSegment);

        // then
        assertThat(response.content().vector()).hasSize(768);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_multiple_segments_with_bge_e5_model() {

        EmbeddingModel model = OvhAiEmbeddingModel.builder()
                .apiKey(System.getenv("OVHAI_AI_API_KEY"))
                .baseUrl("https://bge-base-en-v1-5.endpoints.kepler.ai.cloud.ovh.net")
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        // given
        TextSegment textSegment1 = TextSegment.from("Embed this sentence.");
        TextSegment textSegment2 = TextSegment.from("As well as this one.");

        // when
        Response<List<Embedding>> response = model.embedAll(asList(textSegment1, textSegment2));

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).vector()).hasSize(768);
        assertThat(response.content().get(1).vector()).hasSize(768);

        assertThat(response.finishReason()).isNull();
    }
}
