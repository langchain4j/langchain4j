package dev.langchain4j.store.embedding.vearch.api.space;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * As a constraint of all engine type only
 *
 * @see RetrievalType
 */
public abstract class RetrievalParam {

    @Getter
    @Setter
    @Builder
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
    }

    @Getter
    @Setter
    @Builder
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
        @SerializedName("efConstruction")
        private Integer efConstruction;
    }

    @Getter
    @Setter
    @Builder
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
    }

    @Getter
    @Setter
    @Builder
    public static class IVFFLATParam extends RetrievalParam {

        private MetricType metricType;
        /**
         * number of buckets for indexing
         *
         * <p>default 2048</p>
         */
        private Integer ncentroids;
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
    }

    @Getter
    @Setter
    @Builder
    public static class FLAT extends RetrievalParam {

        private MetricType metricType;
    }
}
