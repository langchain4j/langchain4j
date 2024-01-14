package dev.langchain4j.store.embedding.vearch.api.space;

/**
 * As a constraint of all engine type only
 *
 * @see RetrievalType
 */
public abstract class RetrievalParam {

    public static class IVFPQParam extends RetrievalParam {

        private MetricType metricType;
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

        public MetricType getMetricType() {
            return metricType;
        }

        public void setMetricType(MetricType metricType) {
            this.metricType = metricType;
        }

        public Integer getNcentroids() {
            return ncentroids;
        }

        public void setNcentroids(Integer ncentroids) {
            this.ncentroids = ncentroids;
        }

        public Integer getNsubvector() {
            return nsubvector;
        }

        public void setNsubvector(Integer nsubvector) {
            this.nsubvector = nsubvector;
        }
    }

    public static class HNSWParam extends RetrievalParam {

        private MetricType metricType;
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

        public Integer getNlinks() {
            return nlinks;
        }

        public void setNlinks(Integer nlinks) {
            this.nlinks = nlinks;
        }

        public Integer getEfConstruction() {
            return efConstruction;
        }

        public void setEfConstruction(Integer efConstruction) {
            this.efConstruction = efConstruction;
        }

        public MetricType getMetricType() {
            return metricType;
        }

        public void setMetricType(MetricType metricType) {
            this.metricType = metricType;
        }
    }

    public static class GPUParam extends RetrievalParam {

        private MetricType metricType;
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

        public MetricType getMetricType() {
            return metricType;
        }

        public void setMetricType(MetricType metricType) {
            this.metricType = metricType;
        }

        public Integer getNcentroids() {
            return ncentroids;
        }

        public void setNcentroids(Integer ncentroids) {
            this.ncentroids = ncentroids;
        }

        public Integer getNsubvector() {
            return nsubvector;
        }

        public void setNsubvector(Integer nsubvector) {
            this.nsubvector = nsubvector;
        }
    }

    public static class IVFFLATParam extends RetrievalParam {

        private MetricType metricType;
        /**
         * number of buckets for indexing
         *
         * <p>default 2048</p>
         */
        private Integer ncentroids;

        public MetricType getMetricType() {
            return metricType;
        }

        public void setMetricType(MetricType metricType) {
            this.metricType = metricType;
        }

        public Integer getNcentroids() {
            return ncentroids;
        }

        public void setNcentroids(Integer ncentroids) {
            this.ncentroids = ncentroids;
        }
    }

    public static class BINARYIVFParam extends RetrievalParam {

        /**
         * coarse cluster center number
         *
         * <p>default 256</p>
         */
        private Integer ncentroids;

        public Integer getNcentroids() {
            return ncentroids;
        }

        public void setNcentroids(Integer ncentroids) {
            this.ncentroids = ncentroids;
        }
    }

    public static class FLAT extends RetrievalParam {

        private MetricType metricType;

        public MetricType getMetricType() {
            return metricType;
        }

        public void setMetricType(MetricType metricType) {
            this.metricType = metricType;
        }
    }
}
