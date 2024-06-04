package dev.langchain4j.store.embedding.milvus.parameter;

import io.milvus.param.IndexType;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;

/**
 * for more information, see  <a href="https://milvus.io/docs/index.md#IVF_SQ8">Index#IVF_SQ8</a>
 */
public class IvfSq8IndexParam extends IndexParam {

    /**
     * Number of cluster units, Range: [1, 65536]
     */
    private final Integer nlist;

    public IvfSq8IndexParam(Integer nlist) {
        super(IndexType.IVF_SQ8);
        this.nlist = nlist;
        ensureBetween(nlist, 1, 65536, "nlist must be between 1 and 65536");
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


        public static Builder aFlatIndexParam() {
            return new Builder();
        }

        public Builder nlist(Integer nlist) {
            this.nlist = nlist;
            return this;
        }


        public IvfSq8IndexParam build() {
            return new IvfSq8IndexParam(nlist);
        }
    }
}
