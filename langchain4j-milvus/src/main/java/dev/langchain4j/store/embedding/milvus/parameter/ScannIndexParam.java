package dev.langchain4j.store.embedding.milvus.parameter;

import io.milvus.param.IndexType;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * for more information, see  <a href="https://milvus.io/docs/index.md#SCANN">Index#SCANN</a>
 */
public class ScannIndexParam extends IndexParam {
    /**
     * Number of cluster units, Range: [1, 65536]
     */
    private final Integer nlist;
    /**
     * Whether to include the raw data in the index. Default: true
     */
    private final Boolean with_raw_data;

    public ScannIndexParam(Integer nlist) {
        this(nlist, Boolean.TRUE);
    }

    public ScannIndexParam(Integer nlist, Boolean with_raw_data) {
        super(IndexType.SCANN);
        this.nlist = nlist;
        this.with_raw_data = with_raw_data;
        ensureBetween(nlist, 1, 65536, "nlist must be in range [1,65536]");
        ensureNotNull(with_raw_data, "with_raw_data must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getNlist() {
        return nlist;
    }

    public Boolean getWith_raw_data() {
        return with_raw_data;
    }

    public static final class Builder {
        private Integer nlist;
        private Boolean withRawData = Boolean.TRUE;

        public Builder() {
        }

        public Builder nlist(Integer nlist) {
            this.nlist = nlist;
            return this;
        }

        public Builder withRawData(Boolean withRawData) {
            this.withRawData = withRawData;
            return this;
        }


        public ScannIndexParam build() {
            return new ScannIndexParam(nlist, withRawData);
        }
    }
}
