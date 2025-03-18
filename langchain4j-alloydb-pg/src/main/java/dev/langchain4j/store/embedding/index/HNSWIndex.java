package dev.langchain4j.store.embedding.index;

import java.util.List;

/**
 * HNSW index
 */
public class HNSWIndex implements BaseIndex {

    private final String indexType = "hnsw";
    private final String name;
    private final Integer m;
    private final Integer efConstruction;
    private final DistanceStrategy distanceStrategy;
    private final List<String> partialIndexes;

    /**
     * Constructor for HNSWIndex
     * @param builder builder
     */
    public HNSWIndex(Builder builder) {
        this.name = builder.name;
        this.m = builder.m;
        this.efConstruction = builder.efConstruction;
        this.distanceStrategy = builder.distanceStrategy;
        this.partialIndexes = builder.partialIndexes;
    }

    /** {@inheritDoc} */
    @Override
    public String getIndexOptions() {
        return String.format("(m = %s, ef_construction = %s)", m, efConstruction);
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
     * Builder which configures and creates instances of {@link HNSWIndex}.
     */
    public class Builder {

        private String name;
        private Integer m = 16;
        private Integer efConstruction = 64;
        private DistanceStrategy distanceStrategy = DistanceStrategy.COSINE_DISTANCE;
        private List<String> partialIndexes;

        /**
         * @param m max connections
         * @return thisbuilder
         */
        public Builder m(Integer m) {
            this.m = m;
            return this;
        }

        /**
         * @param efConstruction size of dynamic candidate list for constructing the graph
         * @return thisbuilder
         */
        public Builder efConstruction(Integer efConstruction) {
            this.efConstruction = efConstruction;
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
         * @param name name
         * @return thisbuilder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Builds an {@link HNSWIndex} store with the configuration applied to this builder.
         * @return A new {@link HNSWIndex} instance
         */
        public HNSWIndex build() {
            return new HNSWIndex(this);
        }
    }
}
