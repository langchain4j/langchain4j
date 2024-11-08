package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

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

    protected MetricType metricType;

    protected RetrievalParam() {
    }

    protected RetrievalParam(MetricType metricType) {
        this.metricType = metricType;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    protected abstract static class RetrievalParamBuilder<C extends RetrievalParam, B extends RetrievalParamBuilder<C, B>> {

        protected MetricType metricType = MetricType.INNER_PRODUCT;

        public B metricType(MetricType metricType) {
            this.metricType = metricType;
            return self();
        }

        protected abstract B self();

        public abstract C build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class IVFPQParam extends RetrievalParam {

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
            super(metricType);
            this.ncentroids = ncentroids;
            this.nsubvector = nsubvector;
        }

        public Integer getNcentroids() {
            return ncentroids;
        }

        public Integer getNsubvector() {
            return nsubvector;
        }

        public static IVFPQParamBuilder builder() {
            return new IVFPQParamBuilder();
        }

        public static class IVFPQParamBuilder extends RetrievalParamBuilder<IVFPQParam, IVFPQParamBuilder> {

            private Integer ncentroids;
            private Integer nsubvector;

            public IVFPQParamBuilder ncentroids(Integer ncentroids) {
                this.ncentroids = ncentroids;
                return this;
            }

            public IVFPQParamBuilder nsubvector(Integer nsubvector) {
                this.nsubvector = nsubvector;
                return this;
            }

            @Override
            protected IVFPQParamBuilder self() {
                return this;
            }

            @Override
            public IVFPQParam build() {
                return new IVFPQParam(metricType, ncentroids, nsubvector);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class HNSWParam extends RetrievalParam {

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
            super(metricType);
            this.nlinks = nlinks;
            this.efConstruction = efConstruction;
        }

        public Integer getNlinks() {
            return nlinks;
        }

        public Integer getEfConstruction() {
            return efConstruction;
        }

        public static HNSWParamBuilder builder() {
            return new HNSWParamBuilder();
        }

        public static class HNSWParamBuilder extends RetrievalParamBuilder<HNSWParam, HNSWParamBuilder> {

            private Integer nlinks;
            private Integer efConstruction;

            public HNSWParamBuilder nlinks(Integer nlinks) {
                this.nlinks = nlinks;
                return this;
            }

            public HNSWParamBuilder efConstruction(Integer efConstruction) {
                this.efConstruction = efConstruction;
                return this;
            }

            @Override
            protected HNSWParamBuilder self() {
                return this;
            }

            @Override
            public HNSWParam build() {
                return new HNSWParam(metricType, nlinks, efConstruction);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class GPUParam extends RetrievalParam {

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
            super(metricType);
            this.ncentroids = ncentroids;
            this.nsubvector = nsubvector;
        }

        public Integer getNcentroids() {
            return ncentroids;
        }

        public Integer getNsubvector() {
            return nsubvector;
        }

        public static GPUParamBuilder builder() {
            return new GPUParamBuilder();
        }

        public static class GPUParamBuilder extends RetrievalParamBuilder<GPUParam, GPUParamBuilder> {

            private Integer ncentroids;
            private Integer nsubvector;

            public GPUParamBuilder ncentroids(Integer ncentroids) {
                this.ncentroids = ncentroids;
                return this;
            }

            public GPUParamBuilder nsubvector(Integer nsubvector) {
                this.nsubvector = nsubvector;
                return this;
            }

            @Override
            protected GPUParamBuilder self() {
                return this;
            }

            @Override
            public GPUParam build() {
                return new GPUParam(metricType, ncentroids, nsubvector);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class IVFFLATParam extends RetrievalParam {

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

        public Integer getNcentroids() {
            return ncentroids;
        }

        public static IVFFLATParamBuilder builder() {
            return new IVFFLATParamBuilder();
        }

        public static class IVFFLATParamBuilder extends RetrievalParamBuilder<IVFFLATParam, IVFFLATParamBuilder> {

            private Integer ncentroids;

            public IVFFLATParamBuilder ncentroids(Integer ncentroids) {
                this.ncentroids = ncentroids;
                return this;
            }

            @Override
            protected IVFFLATParamBuilder self() {
                return this;
            }

            @Override
            public IVFFLATParam build() {
                return new IVFFLATParam(metricType, ncentroids);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
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

        public Integer getNcentroids() {
            return ncentroids;
        }

        public static BINARYIVFParamBuilder builder() {
            return new BINARYIVFParamBuilder();
        }

        public static class BINARYIVFParamBuilder {

            private Integer ncentroids;

            public BINARYIVFParamBuilder ncentroids(Integer ncentroids) {
                this.ncentroids = ncentroids;
                return this;
            }

            public BINARYIVFParam build() {
                return new BINARYIVFParam(ncentroids);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class FLAT extends RetrievalParam {

        public FLAT() {
        }

        public FLAT(MetricType metricType) {
            super(metricType);
        }

        public static FLATBuilder builder() {
            return new FLATBuilder();
        }

        public static class FLATBuilder extends RetrievalParamBuilder<FLAT, FLATBuilder> {

            @Override
            protected FLATBuilder self() {
                return this;
            }

            @Override
            public FLAT build() {
                return new FLAT(metricType);
            }
        }
    }
}
