package dev.langchain4j.store.embedding.milvus.parameter;

import io.milvus.param.IndexType;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;

/**
 * for more information, see  <a href="https://milvus.io/docs/index-with-gpu.md#Prepare-index-parameters">GPU Index</a>
 * parameter same as <a href="https://milvus.io/docs/index.md#IVF_FLAT">Index#IVF_FLAT</a>
 */
public class GpuIvfFlatIndexParam extends IndexParam {
    /**
     * Number of cluster units, Range: [1, 65536]
     */
    private final Integer nlist;

    public GpuIvfFlatIndexParam(Integer nlist) {
        super(IndexType.GPU_IVF_FLAT);
        ensureBetween(nlist, 1, 65536, "nlist must be in range  [1,65536]");
        this.nlist = nlist;
    }

    public Integer getNlist() {
        return nlist;
    }

    public static final class Builder {
        private Integer nlist;

        public Builder() {
        }

        public Builder nlist(Integer nlist) {
            this.nlist = nlist;
            return this;
        }

        public GpuIvfFlatIndexParam build() {
            return new GpuIvfFlatIndexParam(nlist);
        }
    }
}
