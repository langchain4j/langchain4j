package dev.langchain4j.store.embedding.milvus.parameter;

import io.milvus.param.IndexType;

/**
 * empty placeholder class
 * for more information, see  <a href="https://milvus.io/docs/index.md#FLAT">Index#FLAT</a>
 */
public class FlatIndexParam extends IndexParam {
    public FlatIndexParam() {
        super(IndexType.FLAT);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        public Builder() {
        }

        public FlatIndexParam build() {
            return new FlatIndexParam();
        }
    }
}
