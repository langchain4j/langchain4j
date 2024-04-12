package dev.langchain4j.store.embedding.milvus.parameter;

import io.milvus.param.IndexType;

/**
 * for more information, see  <a href="https://milvus.io/docs/index.md#BIN_FLAT">Index#BIN_FLAT</a>
 */
public class BinFlatIndexParam extends IndexParam{
    public BinFlatIndexParam() {
        super(IndexType.BIN_FLAT);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        public Builder() {
        }

        public BinFlatIndexParam build() {
            return new BinFlatIndexParam();
        }
    }
}
