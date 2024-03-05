package dev.langchain4j.model.mistralai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class MistralAiEmbeddingModelIT {

    EmbeddingModel model = MistralAiEmbeddingModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_embed_and_return_token_usage() {

        // given
        TextSegment textSegment = TextSegment.from("Embed this sentence.");

        // when
        Response<Embedding> response = model.embed(textSegment);

        // then
        assertThat(response.content().vector()).hasSize(1024);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(0);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(7);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_and_return_token_usage_with_multiple_inputs() {

        // given
        TextSegment textSegment1 = TextSegment.from("Embed this sentence.");
        TextSegment textSegment2 = TextSegment.from("As well as this one.");

        // when
        Response<List<Embedding>> response = model.embedAll(asList(textSegment1, textSegment2));

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).vector()).hasSize(1024);
        assertThat(response.content().get(1).vector()).hasSize(1024);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7 + 8);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(0);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(7 + 8);

        assertThat(response.finishReason()).isNull();

    }
}
