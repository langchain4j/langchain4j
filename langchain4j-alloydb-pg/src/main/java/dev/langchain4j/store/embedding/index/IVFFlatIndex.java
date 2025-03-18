package dev.langchain4j.store.embedding.index;

import java.util.List;

/**
 * IVF Flat index
 */
public class IVFFlatIndex implements BaseIndex {

    private final String indexType = "ivfflat";
    private final String name;
    private final Integer listCount;
    private final DistanceStrategy distanceStrategy;
    private final List<String> partialIndexes;

    /**
     * Constructor for IVFFlatIndex
     * @param builder builder
     */
    public IVFFlatIndex(Builder builder) {
        this.name = builder.name;
        this.listCount = builder.listCount;
        this.distanceStrategy = builder.distanceStrategy;
        this.partialIndexes = builder.partialIndexes;
    }

    @Override
    public String getIndexOptions() {
        return String.format("(lists = %s)", listCount);
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
     * Builder which configures and creates instances of {@link IVFFlatIndex}.
     */
    public class Builder {

        private String name;
        private Integer listCount = 100;
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
         * Builds an {@link IVFFlatIndex} store with the configuration applied to this builder.
         * @return A new {@link IVFFlatIndex} instance
         */
        public IVFFlatIndex build() {
            return new IVFFlatIndex(this);
        }
    }
}
