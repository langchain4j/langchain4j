package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiEmbeddingModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void should_embed_one_text() {

        // given
        GoogleGenAiEmbeddingModel embeddingModel = GoogleGenAiEmbeddingModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-embedding-2")
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        // when
        Response<Embedding> embed = embeddingModel.embed("Hello world!");

        // then
        Embedding content = embed.content();
        assertThat(content).isNotNull();
        assertThat(content.vector()).isNotNull();
        // gemini-embedding-2 generates 3072-dimensional embeddings by default
        assertThat(content.vector()).hasSize(3072);

        assertThat(embeddingModel.dimension()).isEqualTo(3072);
    }

    @Test
    void should_use_metadata() {
        // given
        GoogleGenAiEmbeddingModel embeddingModel = GoogleGenAiEmbeddingModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-embedding-2")
                .logRequests(false) // embeddings are huge in logs
                .logResponses(false)
                .titleMetadataKey("title")
                .taskType(GoogleGenAiEmbeddingModel.TaskTypeEnum.RETRIEVAL_DOCUMENT)
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
        GoogleGenAiEmbeddingModel embeddingModel = GoogleGenAiEmbeddingModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-embedding-2")
                .logRequests(false) // embeddings are huge in logs
                .logResponses(false)
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
        assertThat(embeddings.get(1).vector()).isNotNull();
    }

    @Test
    void should_embed_with_dimensionality_of_512() {

        // given
        int outputDimensionality = 512;

        GoogleGenAiEmbeddingModel embeddingModel = GoogleGenAiEmbeddingModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-embedding-2")
                .logRequests(false)
                .logResponses(false)
                .outputDimensionality(outputDimensionality)
                .build();

        // when
        Response<Embedding> embed = embeddingModel.embed("What is the capital of France?");

        // then
        Embedding content = embed.content();
        assertThat(content).isNotNull();
        assertThat(content.vector()).isNotNull();
        assertThat(content.vector()).hasSize(outputDimensionality);

        assertThat(embeddingModel.dimension()).isEqualTo(outputDimensionality);
    }

    @Test
    void should_embed_more_than_100() {
        // given
        GoogleGenAiEmbeddingModel embeddingModel = GoogleGenAiEmbeddingModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-embedding-2")
                .build();

        // when
        List<TextSegment> textSegments = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            // Using 150 to test a moderate batch. The API has its own limits.
            textSegments.add(TextSegment.from("What is the capital of France? "));
        }

        Response<List<Embedding>> allEmbeddings = embeddingModel.embedAll(textSegments);

        // then
        assertThat(allEmbeddings.content()).hasSize(150);
    }

    @Test
    void should_embed_in_batches_of_custom_size() {
        // given
        GoogleGenAiEmbeddingModel embeddingModel = GoogleGenAiEmbeddingModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-embedding-2")
                .maxSegmentsPerBatch(10)
                .build();

        // when
        List<TextSegment> textSegments = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            textSegments.add(TextSegment.from("Segment " + i));
        }

        Response<List<Embedding>> allEmbeddings = embeddingModel.embedAll(textSegments);

        // then
        assertThat(allEmbeddings.content()).hasSize(25);
    }

    @Test
    void should_embed_with_title_grouping() {
        // given
        GoogleGenAiEmbeddingModel embeddingModel = GoogleGenAiEmbeddingModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-embedding-2")
                .taskType(GoogleGenAiEmbeddingModel.TaskTypeEnum.RETRIEVAL_DOCUMENT)
                .titleMetadataKey("title")
                .maxSegmentsPerBatch(5)
                .build();

        // when
        List<TextSegment> textSegments = new ArrayList<>();
        textSegments.add(TextSegment.from("Document 1 chunk 1", Metadata.from("title", "Doc1")));
        textSegments.add(TextSegment.from("Document 1 chunk 2", Metadata.from("title", "Doc1")));
        textSegments.add(TextSegment.from("Document 2 chunk 1", Metadata.from("title", "Doc2")));
        textSegments.add(TextSegment.from("Document 2 chunk 2", Metadata.from("title", "Doc2")));
        textSegments.add(TextSegment.from("No title chunk"));

        Response<List<Embedding>> allEmbeddings = embeddingModel.embedAll(textSegments);

        // then
        assertThat(allEmbeddings.content()).hasSize(5);
        for (Embedding embedding : allEmbeddings.content()) {
            assertThat(embedding.vector()).isNotNull();
        }
    }
}
