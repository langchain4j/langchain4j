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
public class BINARYIVFSearchParam extends SearchIndexParam {

    private Integer parallelOnQueries;
    @JsonProperty("nprob")
    private Integer nProb;

    public BINARYIVFSearchParam() {
    }

    public BINARYIVFSearchParam(MetricType metricType, Integer parallelOnQueries, Integer nProb) {
        super(metricType);
        this.parallelOnQueries = parallelOnQueries;
        this.nProb = nProb;
    }

    public Integer getParallelOnQueries() {
        return parallelOnQueries;
    }

    public Integer getNProb() {
        return nProb;
    }

    public static BINARYIVFSearchParamBuilder builder() {
        return new BINARYIVFSearchParamBuilder();
    }

    public static class BINARYIVFSearchParamBuilder extends SearchIndexParamBuilder<BINARYIVFSearchParam, BINARYIVFSearchParamBuilder> {

        private Integer parallelOnQueries;
        private Integer nProb;

        public BINARYIVFSearchParamBuilder parallelOnQueries(Integer parallelOnQueries) {
            this.parallelOnQueries = parallelOnQueries;
            return this;
        }

        public BINARYIVFSearchParamBuilder nProb(Integer nProb) {
            this.nProb = nProb;
            return this;
        }

        @Override
        protected BINARYIVFSearchParamBuilder self() {
            return this;
        }

        @Override
        public BINARYIVFSearchParam build() {
            return new BINARYIVFSearchParam(metricType, parallelOnQueries, nProb);
        }
    }
}
