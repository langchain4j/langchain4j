package dev.langchain4j.model.ovhai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
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

        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .baseUrl("https://oai.endpoints.kepler.ai.cloud.ovh.net/v1")
                .apiKey(System.getenv("OVHAI_AI_API_KEY"))
                .modelName("Qwen3-Embedding-8B")
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        // given
        TextSegment textSegment = TextSegment.from("Embed this sentence.");

        // when
        Response<Embedding> response = model.embed(textSegment);

        // then
        assertThat(response.content().vector()).hasSize(4096);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_multiple_segments_with_bge_e5_model() {

        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .baseUrl("https://oai.endpoints.kepler.ai.cloud.ovh.net/v1")
                .apiKey(System.getenv("OVHAI_AI_API_KEY"))
                .modelName("Qwen3-Embedding-8B")
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
        assertThat(response.content().get(0).vector()).hasSize(4096);
        assertThat(response.content().get(1).vector()).hasSize(4096);

        assertThat(response.finishReason()).isNull();
    }
}
