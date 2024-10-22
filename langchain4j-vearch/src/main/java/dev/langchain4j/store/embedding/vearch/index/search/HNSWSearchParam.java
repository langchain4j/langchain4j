package dev.langchain4j.store.embedding.vearch.index.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.store.embedding.vearch.MetricType;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class HNSWSearchParam extends SearchIndexParam {

    @JsonProperty("efSearch")
    private Integer efSearch;

    public HNSWSearchParam() {
    }

    public HNSWSearchParam(MetricType metricType, Integer efSearch) {
        super(metricType);
        this.efSearch = efSearch;
    }

    public Integer getEfSearch() {
        return efSearch;
    }

    public static HNSWSearchParamBuilder builder() {
        return new HNSWSearchParamBuilder();
    }

    public static class HNSWSearchParamBuilder extends SearchIndexParamBuilder<HNSWSearchParam, HNSWSearchParamBuilder> {

        private Integer efSearch;

        public HNSWSearchParamBuilder efSearch(Integer efSearch) {
            this.efSearch = efSearch;
            return this;
        }

        @Override
        protected HNSWSearchParamBuilder self() {
            return this;
        }

        @Override
        public HNSWSearchParam build() {
            return new HNSWSearchParam(metricType, efSearch);
        }
    }
}
