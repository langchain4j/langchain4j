package dev.langchain4j.store.embedding.milvus.parameter;

import io.milvus.param.IndexType;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * for more information, see  <a href="https://milvus.io/docs/index-with-gpu.md#Prepare-index-parameters">GPU Index</a>
 * parameter same as <a href="https://milvus.io/docs/index.md#IVF_PQ">Index#IVF_PQ</a>
 */
public class GpuIvfPqIndexParam extends IndexParam {

    /**
     * Number of cluster units, Range: [1, 65536]
     */
    private final Integer nlist;
    /**
     * Number of factors of product quantization, Range: dim mod m == 0
     */
    private final Integer m;
    /**
     * [Optional] Number of bits in which each low-dimensional vector is stored. Range: [1, 16], Default: 8
     */
    private final Integer nbits;

    public GpuIvfPqIndexParam(Integer nlist, Integer m) {
        this(nlist, m, 8);
    }

    public GpuIvfPqIndexParam(Integer nlist, Integer m, Integer nbits) {
        super(IndexType.GPU_IVF_PQ);
        ensureBetween(nlist, 1, 65536, "nlist must be between in range [1,65536]");
        ensureNotNull(m, "m must not be null, value range is dim mod m == 0");
        if (nbits != null) {
            ensureBetween(nbits, 1, 16, "nbits must be in rnage [1,16]");
        }
        this.nlist = nlist;
        this.m = m;
        this.nbits = nbits;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getNlist() {
        return nlist;
    }

    public Integer getM() {
        return m;
    }

    public Integer getNbits() {
        return nbits;
    }

    public static final class Builder {
        private Integer nbits;
        private Integer m;
        private Integer nlist = 8;

        public Builder() {
        }

        public Builder nbits(Integer nbits) {
            this.nbits = nbits;
            return this;
        }

        public Builder m(Integer m) {
            this.m = m;
            return this;
        }

        public Builder nlist(Integer nlist) {
            this.nlist = nlist;
            return this;
        }

        public GpuIvfPqIndexParam build() {
            return new GpuIvfPqIndexParam(nlist, m, nbits);
        }
    }
}
