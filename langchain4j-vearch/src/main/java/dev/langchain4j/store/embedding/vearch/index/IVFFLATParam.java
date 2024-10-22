package dev.langchain4j.store.embedding.vearch.index;

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
public class IVFFLATParam extends IndexParam {

    /**
     * number of buckets for indexing
     *
     * <p>default 2048</p>
     */
    @JsonProperty("ncentroids")
    private Integer nCentroids;
    private Integer trainingThreshold;
    @JsonProperty("nprob")
    private Integer nProb;

    public IVFFLATParam() {
    }

    public IVFFLATParam(MetricType metricType, Integer nCentroids, Integer trainingThreshold, Integer nProb) {
        super(metricType);
        this.nCentroids = nCentroids;
        this.trainingThreshold = trainingThreshold;
        this.nProb = nProb;
    }

    public Integer getNCentroids() {
        return nCentroids;
    }

    public Integer getTrainingThreshold() {
        return trainingThreshold;
    }

    public Integer getNProb() {
        return nProb;
    }

    public static IVFFlatParamBuilder builder() {
        return new IVFFlatParamBuilder();
    }

    public static class IVFFlatParamBuilder extends IndexParamBuilder<IVFFLATParam, IVFFlatParamBuilder> {

        private Integer nCentroids;
        private Integer trainingThreshold;
        private Integer nProb;

        public IVFFlatParamBuilder nCentroids(Integer nCentroids) {
            this.nCentroids = nCentroids;
            return this;
        }

        public IVFFlatParamBuilder trainingThreshold(Integer trainingThreshold) {
            this.trainingThreshold = trainingThreshold;
            return this;
        }

        public IVFFlatParamBuilder nProb(Integer nProb) {
            this.nProb = nProb;
            return this;
        }

        @Override
        protected IVFFlatParamBuilder self() {
            return this;
        }

        @Override
        public IVFFLATParam build() {
            return new IVFFLATParam(metricType, nCentroids, trainingThreshold, nProb);
        }
    }
}
