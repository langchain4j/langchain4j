package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoogleAiEmbeddingModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void should_embed_one_text() {

        // given
        GoogleAiEmbeddingModel embeddingModel = GoogleAiEmbeddingModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("embedding-001")
                .logRequestsAndResponses(false) // embeddings are huge in logs
                .build();

        // when
        Response<Embedding> embed = embeddingModel.embed("Hello world!");

        // then
        Embedding content = embed.content();
        assertThat(content).isNotNull();
        assertThat(content.vector()).isNotNull();
        assertThat(content.vector()).hasSize(768);

        assertThat(embeddingModel.dimension()).isEqualTo(768);
    }

    @Test
    void should_use_metadata() {
        // given
        GoogleAiEmbeddingModel embeddingModel = GoogleAiEmbeddingModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("embedding-001")
                .logRequestsAndResponses(false) // embeddings are huge in logs
                .titleMetadataKey("title")
                .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT)
                .build();

        // when
        TextSegment textSegment =
                TextSegment.from("What is the capital of France?", Metadata.from("title", "document title"));
        Response<Embedding> embed = embeddingModel.embed(textSegment);

        // then
        Embedding content = embed.content();
        assertThat(content).isNotNull();
        assertThat(content.vector()).isNotNull();
    }

    @Test
    void should_embed_in_batch() {

        // given
        int outputDimensionality = 512;

        GoogleAiEmbeddingModel embeddingModel = GoogleAiEmbeddingModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("embedding-001")
                .logRequestsAndResponses(false) // embeddings are huge in logs
                .outputDimensionality(outputDimensionality)
                .build();

        // when
        List<TextSegment> textSegments = Arrays.asList(
                TextSegment.from("What is the capital of France?"),
                TextSegment.from("What is the capital of Germany?"));

        Response<List<Embedding>> embed = embeddingModel.embedAll(textSegments);

        // then
        List<Embedding> embeddings = embed.content();
        assertThat(embeddings).isNotNull().hasSize(2);
        assertThat(embeddings.get(0).vector()).isNotNull();
        assertThat(embeddings.get(0).vector()).hasSize(outputDimensionality);
        assertThat(embeddings.get(1).vector()).isNotNull();
        assertThat(embeddings.get(1).vector()).hasSize(outputDimensionality);

        assertThat(embeddingModel.dimension()).isEqualTo(outputDimensionality);
    }

    @Test
    void should_embed_more_than_100() {
        // given
        GoogleAiEmbeddingModel embeddingModel = GoogleAiEmbeddingModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("text-embedding-004")
                .build();

        // when
        List<TextSegment> textSegments = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            textSegments.add(TextSegment.from("What is the capital of France? "));
        }

        Response<List<Embedding>> allEmbeddings = embeddingModel.embedAll(textSegments);

        // then
        assertThat(allEmbeddings.content()).hasSize(300);
    }
}
