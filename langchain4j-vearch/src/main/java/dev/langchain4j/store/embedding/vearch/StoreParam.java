package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class StoreParam {

    /**
     * It means you will use so much memory, the excess will be kept to disk. For MemoryOnly, this parameter is invalid.
     */
    private Integer cacheSize;

    public StoreParam() {
    }

    public StoreParam(Integer cacheSize) {
        this.cacheSize = cacheSize;
    }

    public Integer getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(Integer cacheSize) {
        this.cacheSize = cacheSize;
    }

    public static class Builder {

        private Integer cacheSize;

        public Builder cacheSize(Integer cacheSize) {
            this.cacheSize = cacheSize;
            return this;
        }

        public StoreParam build() {
            return new StoreParam(cacheSize);
        }
    }
}
