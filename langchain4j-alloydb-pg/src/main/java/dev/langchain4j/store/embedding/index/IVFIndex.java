package dev.langchain4j.store.embedding.index;

import java.util.List;

/**
 * IVF index
 */
public class IVFIndex implements BaseIndex {

    private final String indexType = "ivf";
    private final String name;
    private final Integer listCount;
    private final String quantizer;
    private final DistanceStrategy distanceStrategy;
    private final List<String> partialIndexes;

    /**
     * Constructor for IVFIndex
     * @param builder builder
     */
    public IVFIndex(Builder builder) {
        this.name = builder.name;
        this.listCount = builder.listCount;
        this.quantizer = builder.quantizer;
        this.distanceStrategy = builder.distanceStrategy;
        this.partialIndexes = builder.partialIndexes;
    }

    /**{@inheritDoc} */
    @Override
    public String getIndexOptions() {
        return String.format("(lists = %s, quantizer = %s)", listCount, quantizer);
    }

    /**
     * the distance strategy for the index
     * @return DistanceStrategy
     */
    public DistanceStrategy getDistanceStrategy() {
        return distanceStrategy;
    }
    /**
     * retrieve partial indexes
     * @return list of partial indexes
     */
    public List<String> getPartialIndexes() {
        return partialIndexes;
    }
    /**
     * retrieve index type
     * @return index type String
     */
    public String getIndexType() {
        return indexType;
    }

    /**
     * retrieve name
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Builder which configures and creates instances of {@link IVFIndex}.
     */
    public class Builder {

        private String name;
        private Integer listCount = 100;
        private String quantizer = "sq8";
        private DistanceStrategy distanceStrategy = DistanceStrategy.COSINE_DISTANCE;
        private List<String> partialIndexes;

        /**
         * @param name name
         * @return thisbuilder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * @param listCount list count
         * @return thisbuilder
         */
        public Builder listCount(Integer listCount) {
            this.listCount = listCount;
            return this;
        }

        /**
         * @param quantizer quantizer
         * @return thisbuilder
         */
        public Builder quantizer(String quantizer) {
            this.quantizer = quantizer;
            return this;
        }

        /**
         * @param distanceStrategy distance strategy
         * @return thisbuilder
         */
        public Builder distanceStrategy(DistanceStrategy distanceStrategy) {
            this.distanceStrategy = distanceStrategy;
            return this;
        }

        /**
         * @param partialIndexes partial indexes
         * @return thisbuilder
         */
        public Builder partialIndexes(List<String> partialIndexes) {
            this.partialIndexes = partialIndexes;
            return this;
        }

        /**
         * Builds an {@link IVFIndex} store with the configuration applied to this builder.
         * @return A new {@link IVFIndex} instance
         */
        public IVFIndex build() {
            return new IVFIndex(this);
        }
    }
}
