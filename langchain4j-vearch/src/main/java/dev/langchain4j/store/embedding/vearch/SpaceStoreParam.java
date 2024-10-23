package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class SpaceStoreParam {

    /**
     * It means you will use so much memory, the excess will be kept to disk. For MemoryOnly, this parameter is invalid.
     */
    private Integer cacheSize;
    private CompressRate compress;

    public SpaceStoreParam() {
    }

    public SpaceStoreParam(Integer cacheSize, CompressRate compress) {
        this.cacheSize = cacheSize;
        this.compress = compress;
    }

    public Integer getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(Integer cacheSize) {
        this.cacheSize = cacheSize;
    }

    public CompressRate getCompress() {
        return compress;
    }

    public void setCompress(CompressRate compress) {
        this.compress = compress;
    }

    public static class Builder {

        private Integer cacheSize;
        private CompressRate compress;

        public Builder cacheSize(Integer cacheSize) {
            this.cacheSize = cacheSize;
            return this;
        }

        public Builder compress(CompressRate compress) {
            this.compress = compress;
            return this;
        }

        public SpaceStoreParam build() {
            return new SpaceStoreParam(cacheSize, compress);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class CompressRate {

        private Integer rate;

        public CompressRate() {
        }

        public CompressRate(Integer rate) {
            this.rate = rate;
        }

        public Integer getRate() {
            return rate;
        }

        public void setRate(Integer rate) {
            this.rate = rate;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private Integer rate;

            public Builder rate(Integer rate) {
                this.rate = rate;
                return this;
            }

            public CompressRate build() {
                return new CompressRate(rate);
            }
        }
    }
}
