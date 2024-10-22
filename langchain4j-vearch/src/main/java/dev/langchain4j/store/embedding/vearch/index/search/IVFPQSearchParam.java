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
public class IVFPQSearchParam extends SearchIndexParam {

    private Integer parallelOnQueries;
    private Integer recallNum;
    @JsonProperty("nprob")
    private Integer nProb;

    public IVFPQSearchParam() {
    }

    public IVFPQSearchParam(MetricType metricType, Integer parallelOnQueries, Integer recallNum, Integer nProb) {
        super(metricType);
        this.parallelOnQueries = parallelOnQueries;
        this.recallNum = recallNum;
        this.nProb = nProb;
    }

    public Integer getParallelOnQueries() {
        return parallelOnQueries;
    }

    public Integer getRecallNum() {
        return recallNum;
    }

    public Integer getNProb() {
        return nProb;
    }

    public static IVFPQSearchParamBuilder builder() {
        return new IVFPQSearchParamBuilder();
    }

    public static class IVFPQSearchParamBuilder extends SearchIndexParamBuilder<IVFPQSearchParam, IVFPQSearchParamBuilder> {

        private Integer parallelOnQueries;
        private Integer recallNum;
        private Integer nProb;

        public IVFPQSearchParamBuilder parallelOnQueries(Integer parallelOnQueries) {
            this.parallelOnQueries = parallelOnQueries;
            return this;
        }

        public IVFPQSearchParamBuilder recallNum(Integer recallNum) {
            this.recallNum = recallNum;
            return this;
        }

        public IVFPQSearchParamBuilder nProb(Integer nProb) {
            this.nProb = nProb;
            return this;
        }

        @Override
        protected IVFPQSearchParamBuilder self() {
            return this;
        }

        @Override
        public IVFPQSearchParam build() {
            return new IVFPQSearchParam(metricType, parallelOnQueries, recallNum, nProb);
        }
    }
}
