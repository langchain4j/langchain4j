package dev.langchain4j.store.embedding.index.query;

import java.util.ArrayList;
import java.util.List;

/**
 * HNSW index query options
 */
public class HNSWQueryOptions implements QueryOptions {

    private final Integer efSearch;

    /**
     * Constructor for HNSWQueryOptions
     * @param builder builder
     */
    public HNSWQueryOptions(Builder builder) {
        this.efSearch = builder.efSearch;
    }

    @Override
    public List<String> getParameterSettings() {
        List<String> parameters = new ArrayList<>();
        parameters.add(String.format("nsw.efS_search = %d", efSearch));
        return parameters;
    }

    /**
     * Builder which configures and creates instances of {@link HNSWQueryOptions}.
     */
    public class Builder {

        private Integer efSearch = 40;

        /**
         * @param efSearch size of the dynamic candidate list for search
         * @return this builder
         */
        public Builder efSearch(Integer efSearch) {
            this.efSearch = efSearch;
            return this;
        }

        /**
         * Builds an {@link HNSWQueryOptions} store with the configuration applied to this builder.
         * @return A new {@link HNSWQueryOptions} instance
         */
        public HNSWQueryOptions build() {
            return new HNSWQueryOptions(this);
        }
    }
}
