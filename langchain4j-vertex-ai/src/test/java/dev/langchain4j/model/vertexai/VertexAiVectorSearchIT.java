package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

import java.util.List;

@Disabled("To run this test, you must have provide your own endpoint, project and location")
public class VertexAiVectorSearchIT extends EmbeddingStoreIT {

    private VertexAiVectorSearch matchingEngine;
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void beforeEach() {
        embeddingModel = VertexAiEmbeddingModel.builder()
                .endpoint("us-central1-aiplatform.googleapis.com:443")
                .project("[PROJECT]")
                .location("us-central1")
                .publisher("google")
                .modelName("textembedding-gecko@001")
                .maxRetries(3)
                .build();

        matchingEngine = VertexAiVectorSearch
                .builder()
                .endpoint("us-central1-aiplatform.googleapis.com:443")
                .bucketName("[BUCKET_NAME]")
                .indexId("[INDEX_ID]")
                .deployedIndexId("[INDEX_DEPLOYED_ID]")
                .indexEndpointId("[INDEX_ENDPOINT_ID]")
                .location("us-central1")
                .project("[PROJECT]")
                .build();


        final List<String> indices = matchingEngine.allIndices();
        indices.forEach(matchingEngine::deleteIndex);
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return matchingEngine;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected double getCosineSimilarity(Embedding embedding, Embedding referenceEmbedding) {
        return CosineSimilarity.between(embedding, referenceEmbedding);
    }
}
