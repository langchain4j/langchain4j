package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * As a constraint of all engine type only
 *
 * @see RetrievalType
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public abstract class RetrievalParam {

    protected RetrievalParam() {

    }

    @Getter
    @Setter
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class IVFPQParam extends RetrievalParam {

        @Builder.Default
        private MetricType metricType = MetricType.INNER_PRODUCT;
        /**
         * number of buckets for indexing
         *
         * <p>default 2048</p>
         */
        private Integer ncentroids;
        /**
         * the number of sub vector
         *
         * <p>default 64, must be a multiple of 4</p>
         */
        private Integer nsubvector;

        public IVFPQParam() {
        }

        public IVFPQParam(MetricType metricType, Integer ncentroids, Integer nsubvector) {
            this.metricType = metricType;
            this.ncentroids = ncentroids;
            this.nsubvector = nsubvector;
        }
    }

    @Getter
    @Setter
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class HNSWParam extends RetrievalParam {

        @Builder.Default
        private MetricType metricType = MetricType.INNER_PRODUCT;
        /**
         * neighbors number of each node
         *
         * <p>default 32</p>
         */
        private Integer nlinks;
        /**
         * expansion factor at construction time
         *
         * <p>default 40</p>
         * <p>The higher the value, the better the construction effect, and the longer it takes</p>
         */
        private Integer efConstruction;

        public HNSWParam() {
        }

        public HNSWParam(MetricType metricType, Integer nlinks, Integer efConstruction) {
            this.metricType = metricType;
            this.nlinks = nlinks;
            this.efConstruction = efConstruction;
        }
    }

    @Getter
    @Setter
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class GPUParam extends RetrievalParam {

        @Builder.Default
        private MetricType metricType = MetricType.INNER_PRODUCT;
        /**
         * number of buckets for indexing
         *
         * <p>default 2048</p>
         */
        private Integer ncentroids;
        /**
         * the number of sub vector
         *
         * <p>default 64</p>
         */
        private Integer nsubvector;

        public GPUParam() {
        }

        public GPUParam(MetricType metricType, Integer ncentroids, Integer nsubvector) {
            this.metricType = metricType;
            this.ncentroids = ncentroids;
            this.nsubvector = nsubvector;
        }
    }

    @Getter
    @Setter
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class IVFFLATParam extends RetrievalParam {

        @Builder.Default
        private MetricType metricType = MetricType.INNER_PRODUCT;
        /**
         * number of buckets for indexing
         *
         * <p>default 2048</p>
         */
        private Integer ncentroids;

        public IVFFLATParam() {
        }

        public IVFFLATParam(MetricType metricType, Integer ncentroids) {
            this.metricType = metricType;
            this.ncentroids = ncentroids;
        }
    }

    @Getter
    @Setter
    @Builder
    public static class BINARYIVFParam extends RetrievalParam {

        /**
         * coarse cluster center number
         *
         * <p>default 256</p>
         */
        private Integer ncentroids;

        public BINARYIVFParam() {

        }

        public BINARYIVFParam(Integer ncentroids) {
            this.ncentroids = ncentroids;
        }
    }

    @Getter
    @Setter
    @Builder
    public static class FLAT extends RetrievalParam {

        @Builder.Default
        private MetricType metricType = MetricType.INNER_PRODUCT;

        public FLAT() {
        }

        public FLAT(MetricType metricType) {
            this.metricType = metricType;
        }
    }
}
