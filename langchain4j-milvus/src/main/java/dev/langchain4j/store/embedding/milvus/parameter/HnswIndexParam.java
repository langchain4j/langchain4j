package dev.langchain4j.store.embedding.milvus.parameter;

import io.milvus.param.IndexType;

import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

/**
 * for more information, see  <a href="https://milvus.io/docs/index.md#HNSW">Index#HNSW</a>
 */
public class HnswIndexParam extends IndexParam {
    /**
     * Maximum degree of the node, Range: (2, 2048)
     */
    private final Integer M;
    /**
     * Size of the dynamic list for the nearest neighbors during the index time. Higher efConstruction leads to a may improve index quality at the cost of increased indexing time. Range: (1, int_max)
     */
    private final Integer efConstruction;

    public HnswIndexParam(Integer M, Integer efConstruction) {
        super(IndexType.HNSW);
        this.M = M;
        this.efConstruction = efConstruction;
        ensureTrue(this.M > 2 && this.M < 2048, "M must be in range (2,2048)");
        ensureTrue(this.efConstruction > 1 && this.efConstruction < Integer.MAX_VALUE, "efConstruction must be in range (1,int_max)");
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getM() {
        return M;
    }

    public Integer getEfConstruction() {
        return efConstruction;
    }

    public static final class Builder {
        private Integer efConstruction;
        private Integer M;

        public Builder() {
        }


        public Builder efConstruction(Integer efConstruction) {
            this.efConstruction = efConstruction;
            return this;
        }

        public Builder M(Integer M) {
            this.M = M;
            return this;
        }

        public HnswIndexParam build() {
            return new HnswIndexParam(M, efConstruction);
        }
    }
}
