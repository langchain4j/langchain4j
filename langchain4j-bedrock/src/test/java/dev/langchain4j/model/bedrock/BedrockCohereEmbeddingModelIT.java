package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;
import static dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel.InputType.SEARCH_QUERY;
import static dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel.Model.COHERE_EMBED_ENGLISH_V3;
import static dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel.Model.COHERE_EMBED_MULTILINGUAL_V3;
import static dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel.Truncate.END;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockCohereEmbeddingModelIT {

    @Test
    void cohereMultilingualEmbeddingModel() {

        BedrockCohereEmbeddingModel embeddingModel = BedrockCohereEmbeddingModel.builder()
                .model(COHERE_EMBED_ENGLISH_V3)
                .inputType(SEARCH_QUERY)
                .build();

        assertThat(embeddingModel).isNotNull();

        Response<Embedding> response = embeddingModel.embed(TextSegment.from("How are you?"));
        assertThat(response).isNotNull();

        Embedding embedding = response.content();

        assertThat(embedding.vector()).hasSize(1024);

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();

        assertThat(embeddingModel.dimension()).isEqualTo(1024);
    }

    @Test
    void cohereMultilingualEmbeddingModelBatch() {

        BedrockCohereEmbeddingModel embeddingModel = BedrockCohereEmbeddingModel.builder()
                .model(COHERE_EMBED_MULTILINGUAL_V3)
                .inputType(SEARCH_QUERY)
                .truncate(END)
                .build();

        assertThat(embeddingModel).isNotNull();

        List<TextSegment> segments = List.of(TextSegment.from("How are you?"), TextSegment.from("What is your name?"));

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        assertThat(response).isNotNull();

        List<Embedding> embeddings = response.content();
        assertThat(embeddings).hasSize(2);

        Embedding embedding = embeddings.get(0);
        assertThat(embedding.vector()).hasSize(1024);

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();

        assertThat(embeddingModel.dimension()).isEqualTo(1024);
    }

    @Test
    void should_handle_large_batch_processing() {
        // Test batch processing with more than BATCH_SIZE (96) segments
        BedrockCohereEmbeddingModel embeddingModel = BedrockCohereEmbeddingModel.builder()
                .model(COHERE_EMBED_ENGLISH_V3)
                .inputType(SEARCH_QUERY)
                .truncate(END)
                .build();

        assertThat(embeddingModel).isNotNull();

        // Create 150 text segments to test batching (96 + 54)
        int numberOfSegments = 150;
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < numberOfSegments; i++) {
            segments.add(TextSegment.from("This is test text segment number " + (i + 1)));
        }

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        assertThat(response).isNotNull();

        List<Embedding> embeddings = response.content();

        // Verify all segments were processed
        assertThat(embeddings).hasSize(numberOfSegments);

        // Verify each embedding has the correct dimension
        for (Embedding embedding : embeddings) {
            assertThat(embedding.vector()).hasSize(1024);
        }

        // Verify embeddings are not null or empty
        assertThat(embeddings).allMatch(embedding -> 
            embedding.vector() != null && embedding.vector().length > 0);

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
        assertThat(embeddingModel.dimension()).isEqualTo(1024);
    }

    @Test
    void should_handle_exact_batch_size() {
        // Test with exactly BATCH_SIZE (96) segments
        BedrockCohereEmbeddingModel embeddingModel = BedrockCohereEmbeddingModel.builder()
                .model(COHERE_EMBED_MULTILINGUAL_V3)
                .inputType(SEARCH_QUERY)
                .truncate(END)
                .build();

        assertThat(embeddingModel).isNotNull();

        // Create exactly 96 text segments
        int numberOfSegments = 96;
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < numberOfSegments; i++) {
            segments.add(TextSegment.from("Batch size test segment " + (i + 1)));
        }

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        assertThat(response).isNotNull();

        List<Embedding> embeddings = response.content();

        // Verify all segments were processed
        assertThat(embeddings).hasSize(numberOfSegments);

        // Verify each embedding has the correct dimension
        assertThat(embeddings).allMatch(embedding -> embedding.vector().length == 1024);

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
        assertThat(embeddingModel.dimension()).isEqualTo(1024);
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
