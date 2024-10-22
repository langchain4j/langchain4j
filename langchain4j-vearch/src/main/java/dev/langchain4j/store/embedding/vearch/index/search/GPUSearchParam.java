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
public class GPUSearchParam extends SearchIndexParam {

    private Integer recallNum;
    @JsonProperty("nprob")
    private Integer nProb;

    public GPUSearchParam() {
    }

    public GPUSearchParam(MetricType metricType, Integer recallNum, Integer nProb) {
        super(metricType);
        this.recallNum = recallNum;
        this.nProb = nProb;
    }

    public Integer getRecallNum() {
        return recallNum;
    }

    public Integer getNProb() {
        return nProb;
    }

    public static GPUSearchParamBuilder builder() {
        return new GPUSearchParamBuilder();
    }

    public static class GPUSearchParamBuilder extends SearchIndexParamBuilder<GPUSearchParam, GPUSearchParamBuilder> {

        private Integer recallNum;
        private Integer nProb;

        public GPUSearchParamBuilder recallNum(Integer recallNum) {
            this.recallNum = recallNum;
            return this;
        }

        public GPUSearchParamBuilder nProb(Integer nProb) {
            this.nProb = nProb;
            return this;
        }

        @Override
        protected GPUSearchParamBuilder self() {
            return this;
        }

        @Override
        public GPUSearchParam build() {
            return new GPUSearchParam(metricType, recallNum, nProb);
        }
    }
}
