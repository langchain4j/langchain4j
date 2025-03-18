package dev.langchain4j.store.embedding.index.query;

import java.util.ArrayList;
import java.util.List;

/**
 * ScaNN index query options
 */
public class IVFQueryOptions implements QueryOptions {

    private final Integer probes;

    /**
     * Constructor for IVFQueryOptions
     * @param builder builder
     */
    public IVFQueryOptions(Builder builder) {
        this.probes = builder.probes;
    }

    /** {@inheritDoc}  */
    @Override
    public List<String> getParameterSettings() {
        List<String> parameters = new ArrayList<>();
        parameters.add(String.format("ivf.probes = %d", probes));
        return parameters;
    }

    /**
     * Builder which configures and creates instances of {@link IVFQueryOptions}.
     */
    public class Builder {

        private Integer probes = 1;

        /**
         * @param probes number of probes
         * @return this builder
         */
        public Builder probes(Integer probes) {
            this.probes = probes;
            return this;
        }

        /**
         * Builds an {@link IVFQueryOptions} store with the configuration applied to this builder.
         * @return A new {@link IVFQueryOptions} instance
         */
        public IVFQueryOptions build() {
            return new IVFQueryOptions(this);
        }
    }
}
