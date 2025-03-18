package dev.langchain4j.store.embedding.index.query;

import java.util.ArrayList;
import java.util.List;

/**
 * ScaNN index query options
 */
public class ScaNNIndexQueryOptions implements QueryOptions {

    private final Integer numLeavesToSearch;
    private final Integer preOrderingNumNeighbors;

    /**
     * Constructor for ScaNNIndexQueryOptions
     * @param builder builder
     */
    public ScaNNIndexQueryOptions(Builder builder) {
        this.numLeavesToSearch = builder.numLeavesToSearch;
        this.preOrderingNumNeighbors = builder.preOrderingNumNeighbors;
    }

    /** {@inheritDoc}  */
    @Override
    public List<String> getParameterSettings() {
        List<String> parameters = new ArrayList<>();
        parameters.add(String.format("scann.num_leaves_to_search = %s", numLeavesToSearch));
        parameters.add(String.format("scann.pre_reordering_num_neighbors = %s", preOrderingNumNeighbors));
        return parameters;
    }

    /**
     * Builder which configures and creates instances of {@link ScaNNIndexQueryOptions}.
     */
    public class Builder {

        private Integer numLeavesToSearch = 1;
        private Integer preOrderingNumNeighbors = -1;

        /**
         * @param numLeavesToSearch number of probes
         * @return this builder
         */
        public Builder numLeavesToSearch(Integer numLeavesToSearch) {
            this.numLeavesToSearch = numLeavesToSearch;
            return this;
        }

        /**
         * @param preOrderingNumNeighbors  number of preordering neighbors
         * @return this builder
         */
        public Builder preOrderingNumNeighbors(Integer preOrderingNumNeighbors) {
            this.preOrderingNumNeighbors = preOrderingNumNeighbors;
            return this;
        }
        /**
         * Builds an {@link ScaNNIndexQueryOptions} store with the configuration applied to this builder.
         * @return A new {@link ScaNNIndexQueryOptions} instance
         */
        public ScaNNIndexQueryOptions build() {
            return new ScaNNIndexQueryOptions(this);
        }
    }
}
