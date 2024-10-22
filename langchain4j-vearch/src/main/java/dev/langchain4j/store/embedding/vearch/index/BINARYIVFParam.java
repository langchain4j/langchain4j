package dev.langchain4j.store.embedding.vearch.index;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.store.embedding.vearch.MetricType;

public class BINARYIVFParam extends IndexParam {

    /**
     * coarse cluster center number
     *
     * <p>default 256</p>
     */
    @JsonProperty("ncentroids")
    private Integer nCentroids;
    private Integer trainingThreshold;
    @JsonProperty("nprob")
    private Integer nProb;

    public BINARYIVFParam() {
    }

    public BINARYIVFParam(MetricType metricType, Integer nCentroids, Integer trainingThreshold, Integer nProb) {
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

    public static BinaryIVFParamBuilder builder() {
        return new BinaryIVFParamBuilder();
    }

    public static class BinaryIVFParamBuilder extends IndexParamBuilder<BINARYIVFParam, BinaryIVFParamBuilder> {

        private Integer nCentroids;
        private Integer trainingThreshold;
        private Integer nProb;

        public BinaryIVFParamBuilder nCentroids(Integer nCentroids) {
            this.nCentroids = nCentroids;
            return this;
        }

        public BinaryIVFParamBuilder trainingThreshold(Integer trainingThreshold) {
            this.trainingThreshold = trainingThreshold;
            return this;
        }

        public BinaryIVFParamBuilder nProb(Integer nProb) {
            this.nProb = nProb;
            return this;
        }

        @Override
        protected BinaryIVFParamBuilder self() {
            return this;
        }

        public BINARYIVFParam build() {
            return new BINARYIVFParam(metricType, nCentroids, trainingThreshold, nProb);
        }
    }
}
