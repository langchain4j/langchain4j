package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.*;

import static dev.langchain4j.model.vertexai.VertexAiEmbeddingModel.TaskType.*;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class VertexAiEmbeddingModelIT {

    @Test
    void testEmbeddingModel() {
        EmbeddingModel embeddingModel = VertexAiEmbeddingModel.builder()
                .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .publisher("google")
                .modelName("textembedding-gecko@001")
                .maxRetries(3)
                .build();

        List<TextSegment> segments = asList(
                TextSegment.from("one"),
                TextSegment.from("two"),
                TextSegment.from("three"),
                TextSegment.from("four"),
                TextSegment.from("five"),
                TextSegment.from("six")
        );

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);

        List<Embedding> embeddings = response.content();
        assertThat(embeddings).hasSize(6);

        Embedding embedding = embeddings.get(0);
        assertThat(embedding.vector()).hasSize(768);
        System.out.println(Arrays.toString(embedding.vector()));

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(6);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(6);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void testTokensCountCalculationAndBatching() {
        VertexAiEmbeddingModel model = VertexAiEmbeddingModel.builder()
            .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .publisher("google")
            .modelName("textembedding-gecko@001")
            .maxRetries(3)
            .build();

        List<Integer> tokenCounts = model.calculateTokensCounts(createRandomSegments(5000, 1000));

        assertThat(tokenCounts).hasSize(5000);
        // 5000 segments of 1000 characters is about 5 million characters.
        // With an estimate of 5 letters per token, we have at least 1 million tokens in total
        assertThat(tokenCounts.stream().mapToInt(i -> i).sum()).isGreaterThanOrEqualTo(1_000_000);
    }

    @Test
    void testRandomSegments() {
        List<TextSegment> segments = createRandomSegments(10, 100);
        System.out.println(segments);

        assertThat(segments.size()).isEqualTo(10);
        for (TextSegment segment : segments) {
            assertThat(segment.text()).hasSizeLessThan(100);
        }
    }

    private static List<TextSegment> createRandomSegments(int count, int maxLength) {
        TextSegment[] textSegmentArray = new TextSegment[count];

        String[] words = ("Once upon a time in the town of VeggieVille, there lived a cheerful carrot " +
            "named Charlie. Charlie was a radiant carrot, always beaming with joy and positivity. His " +
            "vibrant orange skin and lush green top were a sight to behold, but it was his infectious " +
            "laughter and warm personality that really set him apart.")
            .split(" ");

        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder();
            do {
                String nextWord = words[new Random().nextInt(words.length)];
                if (sb.length() + nextWord.length() + 1 < maxLength) {
                    sb.append(nextWord).append(" ");
                } else break;
            } while (sb.length() < maxLength);
            textSegmentArray[i] = TextSegment.from(sb.toString());
        }

        return Arrays.asList(textSegmentArray);
    }

    @Test
    void testBatchingEmbeddings() {
        VertexAiEmbeddingModel model = VertexAiEmbeddingModel.builder()
            .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .publisher("google")
            .modelName("textembedding-gecko@003")
            .maxRetries(3)
            .build();

        // 1234 segments requires splitting in batches of 250 or less
        // 1234 times 21 tokens is above the 20k token limit
        List<TextSegment> segments = Collections.nCopies(1234, TextSegment.from(
            "Once upon a time, in a haunted forrest, lived a gentle squirrel."));

        List<Integer> tokenCounts = model.calculateTokensCounts(segments);

        assertThat(tokenCounts).hasSize(1234);
        for (Integer tokenCount : tokenCounts) {
            assertThat(tokenCount).isEqualTo(21);
        }

        List<Embedding> embeddings = model.embedAll(segments).content();

        assertThat(embeddings.size()).isEqualTo(1234);
    }

    @Test
    void testBatchingEmbeddingsWithMaxSet() {
        VertexAiEmbeddingModel model = VertexAiEmbeddingModel.builder()
            .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .publisher("google")
            .modelName("textembedding-gecko@003")
            .maxRetries(3)
            .maxSegmentsPerBatch(50)
            .maxTokensPerBatch(1000)
            .build();

        List<TextSegment> segments = Collections.nCopies(1234, TextSegment.from(
            "Once upon a time, in a haunted forrest, lived a gentle squirrel."));

        List<Integer> tokenCounts = model.calculateTokensCounts(segments);

        assertThat(tokenCounts).hasSize(1234);
        for (Integer tokenCount : tokenCounts) {
            assertThat(tokenCount).isEqualTo(21);
        }

        List<Embedding> embeddings = model.embedAll(segments).content();

        assertThat(embeddings.size()).isEqualTo(1234);
    }

    @Test
    void testEmbeddingTask() {
        // Semantic similarity embedding

        VertexAiEmbeddingModel model = VertexAiEmbeddingModel.builder()
            .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .publisher("google")
            .modelName("textembedding-gecko@003")
            .maxRetries(3)
            .taskType(SEMANTIC_SIMILARITY)
            .build();

        String text = "Embeddings for Text is the name for the model that supports text embeddings. " +
            "Text embeddings are a NLP technique that converts textual data into numerical vectors " +
            "that can be processed by machine learning algorithms, especially large models. `" +
            "These vector representations are designed to capture the semantic meaning and context " +
            "of the words they represent.";

        Response<Embedding> embeddedText = model.embed(text);

        assertThat(embeddedText.content().dimension()).isEqualTo(768);

        // Text classification embedding

        TextSegment segment2 = new TextSegment("Text Classification: Training a model that maps " +
            "the text embeddings to the correct category labels (e.g., cat vs. dog, spam vs. not spam). " +
            "Once the model is trained, it can be used to classify new text inputs into one or more " +
            "categories based on their embeddings.",
            new Metadata());

        model = VertexAiEmbeddingModel.builder()
            .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .publisher("google")
            .modelName("textembedding-gecko@003")
            .maxRetries(3)
            .taskType(CLASSIFICATION)
            .build();

        Response<Embedding> embeddedSegForClassif = model.embed(segment2);

        assertThat(embeddedSegForClassif.content().dimension()).isEqualTo(768);

        // Document retrieval embedding

        Metadata metadata = new Metadata();
        metadata.add("title", "Text embeddings");

        TextSegment segmentForRetrieval = new TextSegment("Text embeddings can be used to represent both the " +
            "user's query and the universe of documents in a high-dimensional vector space. Documents " +
            "that are more semantically similar to the user's query will have a shorter distance in the " +
            "vector space, and can be ranked higher in the search results.", metadata);

        model = VertexAiEmbeddingModel.builder()
            .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .publisher("google")
            .modelName("textembedding-gecko@003")
            .maxRetries(3)
            .taskType(RETRIEVAL_DOCUMENT)
            .build();

        Response<Embedding> embeddedSegForRetrieval = model.embed(segmentForRetrieval);

        assertThat(embeddedSegForRetrieval.content().dimension()).isEqualTo(768);

        // Choose a custom metadata key instead of "title"
        // as the embedding model requires "title" to be used only for RETRIEVAL_DOCUMENT

        Metadata metadataCustomTitleKey = new Metadata();
        metadataCustomTitleKey.add("customTitle", "Text embeddings");

        TextSegment segmentForRetrievalWithCustomKey = new TextSegment("Text embeddings can be used to represent both the " +
            "user's query and the universe of documents in a high-dimensional vector space. Documents " +
            "that are more semantically similar to the user's query will have a shorter distance in the " +
            "vector space, and can be ranked higher in the search results.", metadataCustomTitleKey);

        model = VertexAiEmbeddingModel.builder()
            .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .publisher("google")
            .modelName("textembedding-gecko@003")
            .maxRetries(3)
            .taskType(RETRIEVAL_DOCUMENT)
            .titleMetadataKey("customTitle")
            .build();

        Response<Embedding> embeddedSegForRetrievalWithCustomKey = model.embed(segmentForRetrievalWithCustomKey);

        assertThat(embeddedSegForRetrievalWithCustomKey.content().dimension()).isEqualTo(768);

        // Check we can use "title" metadata when not using RETRIEVAL_DOCUMENT task

        model = VertexAiEmbeddingModel.builder()
            .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .publisher("google")
            .modelName("textembedding-gecko@003")
            .maxRetries(3)
            .titleMetadataKey("customTitle")
            .build();

        Response<Embedding> embeddedSegTitleKeyNoRetrieval = model.embed(segmentForRetrieval);

        assertThat(embeddedSegTitleKeyNoRetrieval.content().dimension()).isEqualTo(768);
    }
}