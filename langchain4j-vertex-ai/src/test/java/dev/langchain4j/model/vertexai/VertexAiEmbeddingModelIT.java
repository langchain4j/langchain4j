package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class VertexAiEmbeddingModelIT {

    @Test
    void testEmbeddingModel() {
        EmbeddingModel embeddingModel = VertexAiEmbeddingModel.builder()
            .endpoint("us-central1-aiplatform.googleapis.com:443")
            .project("genai-java-demos")
            .location("us-central1")
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
            .endpoint("us-central1-aiplatform.googleapis.com:443")
            .project("genai-java-demos")
            .location("us-central1")
            .publisher("google")
            .modelName("textembedding-gecko@001")
            .maxRetries(3)
            .build();

        List<Integer> tokenCounts = model.getTokensCounts(createRandomSegments(5000, 1000));

        assertThat(tokenCounts).hasSize(5000);
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
            .endpoint("us-central1-aiplatform.googleapis.com:443")
            .project("genai-java-demos")
            .location("us-central1")
            .publisher("google")
            .modelName("textembedding-gecko@003")
            .maxRetries(3)
            .build();

        // 1234 segments requires splitting in batches of 250 or less
        // 1234 times 21 tokens is above the 20k token limit
        List<TextSegment> segments = Collections.nCopies(1234, TextSegment.from(
            "Once upon a time, in a haunted forrest, lived a gentle squirrel."));

        List<Integer> tokenCounts = model.getTokensCounts(segments);

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
            .endpoint("us-central1-aiplatform.googleapis.com:443")
            .project("genai-java-demos")
            .location("us-central1")
            .publisher("google")
            .modelName("textembedding-gecko@003")
            .maxRetries(3)
            .maxBatchSize(50)
            .maxTokensPerBatch(1000)
            .build();

        List<TextSegment> segments = Collections.nCopies(1234, TextSegment.from(
            "Once upon a time, in a haunted forrest, lived a gentle squirrel."));

        List<Integer> tokenCounts = model.getTokensCounts(segments);

        assertThat(tokenCounts).hasSize(1234);
        for (Integer tokenCount : tokenCounts) {
            assertThat(tokenCount).isEqualTo(21);
        }

        List<Embedding> embeddings = model.embedAll(segments).content();

        assertThat(embeddings.size()).isEqualTo(1234);
    }
}