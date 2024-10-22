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
public class IVFFLATSearchParam extends SearchIndexParam {

    private Integer parallelOnQueries;
    @JsonProperty("nprob")
    private Integer nProb;

    public IVFFLATSearchParam() {
    }

    public IVFFLATSearchParam(MetricType metricType, Integer parallelOnQueries, Integer nProb) {
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

    public static IVFFLATSearchParamBuilder builder() {
        return new IVFFLATSearchParamBuilder();
    }

    public static class IVFFLATSearchParamBuilder extends SearchIndexParamBuilder<IVFFLATSearchParam, IVFFLATSearchParamBuilder> {

        private Integer parallelOnQueries;
        private Integer nProb;

        public IVFFLATSearchParamBuilder parallelOnQueries(Integer parallelOnQueries) {
            this.parallelOnQueries = parallelOnQueries;
            return this;
        }

        public IVFFLATSearchParamBuilder nProb(Integer nProb) {
            this.nProb = nProb;
            return this;
        }

        @Override
        protected IVFFLATSearchParamBuilder self() {
            return this;
        }

        @Override
        public IVFFLATSearchParam build() {
            return new IVFFLATSearchParam(metricType, parallelOnQueries, nProb);
        }
    }
}
