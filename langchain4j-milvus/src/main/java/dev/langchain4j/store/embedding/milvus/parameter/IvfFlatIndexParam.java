package dev.langchain4j.store.embedding.milvus.parameter;

import io.milvus.param.IndexType;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;

/**
 * for more information, see  <a href="https://milvus.io/docs/index.md#IVF_FLAT">Index#IVF_FLAT</a>
 */
public class IvfFlatIndexParam extends IndexParam {

    /**
     * Number of cluster units, Range: [1, 65536]
     */
    private final Integer nlist;

    public IvfFlatIndexParam(Integer nlist) {
        super(IndexType.IVF_FLAT);
        this.nlist = nlist;
        ensureBetween(nlist, 1, 65536, "nlist must be in range [1,65536]");
    }

    public static Builder builder() {
        return new Builder();
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


        public IvfFlatIndexParam build() {
            return new IvfFlatIndexParam(nlist);
        }
    }
}
