package dev.langchain4j.store.embedding.vearch.index;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.store.embedding.vearch.MetricType;

public class GPUParam extends IndexParam {

    /* number of buckets for indexing
     *
     * <p>default 2048</p>
     */
    @JsonProperty("ncentroids")
    private Integer nCentroids;
    /**
     * the number of sub vector
     *
     * <p>default 64</p>
     */
    @JsonProperty("nsubvector")
    private Integer nSubVector;
    private Integer trainingThreshold;
    @JsonProperty("nprob")
    private Integer nProb;

    public GPUParam() {
    }

    public GPUParam(MetricType metricType, Integer nCentroids, Integer nSubVector, Integer trainingThreshold, Integer nProb) {
        super(metricType);
        this.nCentroids = nCentroids;
        this.nSubVector = nSubVector;
        this.trainingThreshold = trainingThreshold;
        this.nProb = nProb;
    }

    public Integer getNCentroids() {
        return nCentroids;
    }

    public Integer getNSubVector() {
        return nSubVector;
    }

    public Integer getTrainingThreshold() {
        return trainingThreshold;
    }

    public Integer getNProb() {
        return nProb;
    }

    public static GPUParamBuilder builder() {
        return new GPUParamBuilder();
    }

    public static class GPUParamBuilder extends IndexParamBuilder<GPUParam, GPUParamBuilder> {

        private Integer nCentroids;
        private Integer nSubVector;
        private Integer trainingThreshold;
        private Integer nProb;

        public GPUParamBuilder nCentroids(Integer nCentroids) {
            this.nCentroids = nCentroids;
            return this;
        }

        public GPUParamBuilder nSubVector(Integer nSubVector) {
            this.nSubVector = nSubVector;
            return this;
        }

        public GPUParamBuilder trainingThreshold(Integer trainingThreshold) {
            this.trainingThreshold = trainingThreshold;
            return this;
        }

        public GPUParamBuilder nProb(Integer nProb) {
            this.nProb = nProb;
            return this;
        }

        @Override
        protected GPUParamBuilder self() {
            return this;
        }

        @Override
        public GPUParam build() {
            return new GPUParam(metricType, nCentroids, nSubVector, trainingThreshold, nProb);
        }
    }
}
