package dev.langchain4j.store.cassio;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Wrap query parameters as a Bean.
 */
@Getter @Setter @Builder
public class AnnQuery {

    /**
     * Maximum number of item returned
     */
    private int recordCount;

    /**
     * Minimum distance computation
     */
    private double threshold = 0.0;

    /**
     * Embeddings to be searched.
     */
    private List<Float> embeddings;

    /**
     * Default distance is cosine
     */
    private SimilarityMetric metric = SimilarityMetric.COSINE;

    /**
     * If provided search on metadata
     */
    private Map<String, String> metaData;

}
