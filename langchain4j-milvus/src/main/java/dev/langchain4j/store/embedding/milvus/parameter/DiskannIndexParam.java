package dev.langchain4j.store.embedding.milvus.parameter;

import io.milvus.param.IndexType;

/**
 * for more information, see  <a href="https://milvus.io/docs/disk_index.md#Index-and-search-settings">Disk Index#DISKANN</a>
 */
public class DiskannIndexParam extends IndexParam {
    public DiskannIndexParam() {
        super(IndexType.DISKANN);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        public Builder() {
        }

        public DiskannIndexParam build() {
            return new DiskannIndexParam();
        }
    }
}
