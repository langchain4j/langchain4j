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
public class IVFPQParam extends IndexParam {

    /**
     * number of buckets for indexing
     *
     * <p>default 2048</p>
     */
    @JsonProperty("ncentroids")
    private Integer nCentroids;
    /**
     * PQ disassembler vector size
     *
     * <p>default 64, must be a multiple of 4</p>
     */
    @JsonProperty("nsubvector")
    private Integer nSubVector;
    /**
     * bucket init size
     */
    private Integer bucketInitSize;
    /**
     * max size for each bucket
     */
    private Integer bucketMaxSize;
    /**
     * training data size
     */
    private Integer trainingThreshold;
    /**
     * the number of cluster centers found during retrieval
     *
     * <p>default 80</p>
     */
    @JsonProperty("nprob")
    private Integer nProb;

    public IVFPQParam() {
    }

    public IVFPQParam(MetricType metricType, Integer nCentroids, Integer nSubVector,
                      Integer bucketInitSize, Integer bucketMaxSize, Integer trainingThreshold, Integer nProb) {
        super(metricType);
        this.nCentroids = nCentroids;
        this.nSubVector = nSubVector;
        this.bucketInitSize = bucketInitSize;
        this.bucketMaxSize = bucketMaxSize;
        this.trainingThreshold = trainingThreshold;
        this.nProb = nProb;
    }

    public Integer getNCentroids() {
        return nCentroids;
    }

    public Integer getNSubVector() {
        return nSubVector;
    }

    public Integer getBucketInitSize() {
        return bucketInitSize;
    }

    public Integer getBucketMaxSize() {
        return bucketMaxSize;
    }

    public Integer getTrainingThreshold() {
        return trainingThreshold;
    }

    public Integer getNProb() {
        return nProb;
    }

    public static IVFPQParamBuilder builder() {
        return new IVFPQParamBuilder();
    }

    public static class IVFPQParamBuilder extends IndexParamBuilder<IVFPQParam, IVFPQParamBuilder> {

        private Integer nCentroids;
        private Integer nSubVector;
        private Integer bucketInitSize;
        private Integer bucketMaxSize;
        private Integer trainingThreshold;
        private Integer nProb;

        public IVFPQParamBuilder nCentroids(Integer nCentroids) {
            this.nCentroids = nCentroids;
            return this;
        }

        public IVFPQParamBuilder nSubVector(Integer nSubVector) {
            this.nSubVector = nSubVector;
            return this;
        }

        public IVFPQParamBuilder bucketInitSize(Integer bucketInitSize) {
            this.bucketInitSize = bucketInitSize;
            return this;
        }

        public IVFPQParamBuilder bucketMaxSize(Integer bucketMaxSize) {
            this.bucketMaxSize = bucketMaxSize;
            return this;
        }

        public IVFPQParamBuilder trainingThreshold(Integer trainingThreshold) {
            this.trainingThreshold = trainingThreshold;
            return this;
        }

        public IVFPQParamBuilder nProb(Integer nProb) {
            this.nProb = nProb;
            return this;
        }

        @Override
        protected IVFPQParamBuilder self() {
            return this;
        }

        @Override
        public IVFPQParam build() {
            return new IVFPQParam(metricType, nCentroids, nSubVector, bucketInitSize, bucketMaxSize, trainingThreshold, nProb);
        }
    }
}
