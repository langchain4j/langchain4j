package dev.langchain4j.model.openai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiEmbeddingModelIT {

    EmbeddingModel model = OpenAiEmbeddingModel.withApiKey(System.getenv("OPENAI_API_KEY"));

    @Test
    void should_embed_and_return_token_usage() {

        Response<Embedding> response = model.embed("hello world");
        System.out.println(response);

        assertThat(response.content().vector()).hasSize(1536);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(2);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(2);

        assertThat(response.finishReason()).isNull();
    }
}