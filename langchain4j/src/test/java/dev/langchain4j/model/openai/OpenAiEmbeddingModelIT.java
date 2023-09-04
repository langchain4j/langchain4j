package dev.langchain4j.model.openai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Result;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiEmbeddingModelIT {

    EmbeddingModel model = OpenAiEmbeddingModel.withApiKey(System.getenv("OPENAI_API_KEY"));

    @Test
    void should_embed_and_return_token_usage() {

        Result<Embedding> result = model.embed("hello world");

        assertThat(result.get().vector()).hasSize(1536);

        TokenUsage tokenUsage = result.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(2);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(2);

        assertThat(result.finishReason()).isNull();
    }
}