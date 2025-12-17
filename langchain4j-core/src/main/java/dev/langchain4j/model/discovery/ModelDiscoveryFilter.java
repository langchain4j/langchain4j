package dev.langchain4j.model.discovery;

import dev.langchain4j.model.chat.Capability;
import java.util.Objects;
import java.util.Set;

/**
 * Filter criteria for model discovery.
 *
 * <p>Not all providers support all filter criteria. Providers that don't support
 * server-side filtering may apply these filters client-side or ignore them.
 *
 * <p>Example:
 * <pre>{@code
 * ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
 *     .types(Set.of(ModelType.CHAT))
 *     .requiredCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
 *     .minContextWindow(100000)
 *     .includeDeprecated(false)
 *     .build();
 * }</pre>
 */
public class ModelDiscoveryFilter {

    /**
     * A pre-built filter that matches all models (no filtering).
     */
    public static final ModelDiscoveryFilter ALL = ModelDiscoveryFilter.builder().build();

    private final Set<ModelType> types;
    private final Set<Capability> requiredCapabilities;
    private final Integer minContextWindow;
    private final Integer maxContextWindow;
    private final String namePattern;
    private final Boolean includeDeprecated;

    private ModelDiscoveryFilter(Builder builder) {
        this.types = builder.types != null ? Set.copyOf(builder.types) : null;
        this.requiredCapabilities = builder.requiredCapabilities != null ? Set.copyOf(builder.requiredCapabilities) : null;
        this.minContextWindow = builder.minContextWindow;
        this.maxContextWindow = builder.maxContextWindow;
        this.namePattern = builder.namePattern;
        this.includeDeprecated = builder.includeDeprecated;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Model types to include in results.
     * If null or empty, type filtering is not applied.
     *
     * @return set of model types, or null if not filtering by type
     */
    public Set<ModelType> getTypes() {
        return types;
    }

    /**
     * Capabilities that models must support.
     * Models must support ALL specified capabilities to match.
     * If null or empty, capability filtering is not applied.
     *
     * @return set of required capabilities, or null if not filtering by capabilities
     */
    public Set<Capability> getRequiredCapabilities() {
        return requiredCapabilities;
    }

    /**
     * Minimum context window size (inclusive).
     * Models with smaller context windows are excluded.
     *
     * @return minimum context window in tokens, or null if no minimum is set
     */
    public Integer getMinContextWindow() {
        return minContextWindow;
    }

    /**
     * Maximum context window size (inclusive).
     * Models with larger context windows are excluded.
     *
     * @return maximum context window in tokens, or null if no maximum is set
     */
    public Integer getMaxContextWindow() {
        return maxContextWindow;
    }

    /**
     * Regular expression pattern for matching model names.
     * Only models whose names match this pattern are included.
     *
     * @return regex pattern string, or null if not filtering by name
     */
    public String getNamePattern() {
        return namePattern;
    }

    /**
     * Whether to include deprecated models in results.
     * If false, deprecated models are excluded.
     * If null (default), deprecation status is ignored.
     *
     * @return true to include, false to exclude, or null to ignore deprecation status
     */
    public Boolean getIncludeDeprecated() {
        return includeDeprecated;
    }

    /**
     * Checks if this filter matches all models (no filtering criteria specified).
     *
     * @return true if no filtering criteria are set, false otherwise
     */
    public boolean matchesAll() {
        return types == null &&
               requiredCapabilities == null &&
               minContextWindow == null &&
               maxContextWindow == null &&
               namePattern == null &&
               includeDeprecated == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelDiscoveryFilter)) return false;
        ModelDiscoveryFilter that = (ModelDiscoveryFilter) o;
        return Objects.equals(types, that.types) &&
               Objects.equals(requiredCapabilities, that.requiredCapabilities) &&
               Objects.equals(minContextWindow, that.minContextWindow) &&
               Objects.equals(maxContextWindow, that.maxContextWindow) &&
               Objects.equals(namePattern, that.namePattern) &&
               Objects.equals(includeDeprecated, that.includeDeprecated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(types, requiredCapabilities, minContextWindow, maxContextWindow, namePattern, includeDeprecated);
    }

    @Override
    public String toString() {
        return "ModelDiscoveryFilter{" +
               "types=" + types +
               ", requiredCapabilities=" + requiredCapabilities +
               ", minContextWindow=" + minContextWindow +
               ", maxContextWindow=" + maxContextWindow +
               ", namePattern='" + namePattern + '\'' +
               ", includeDeprecated=" + includeDeprecated +
               '}';
    }

    public static class Builder {
        private Set<ModelType> types;
        private Set<Capability> requiredCapabilities;
        private Integer minContextWindow;
        private Integer maxContextWindow;
        private String namePattern;
        private Boolean includeDeprecated;

        /**
         * Filter by model types. Only models matching one of these types will be returned.
         *
         * @param types Set of model types to include
         * @return this builder
         */
        public Builder types(Set<ModelType> types) {
            this.types = types;
            return this;
        }

        /**
         * Filter by required capabilities. Only models that support ALL specified capabilities will be returned.
         *
         * @param requiredCapabilities Set of capabilities that models must support
         * @return this builder
         */
        public Builder requiredCapabilities(Set<Capability> requiredCapabilities) {
            this.requiredCapabilities = requiredCapabilities;
            return this;
        }

        /**
         * Filter by minimum context window size. Only models with context windows >= this value will be returned.
         *
         * @param minContextWindow Minimum context window in tokens
         * @return this builder
         */
        public Builder minContextWindow(Integer minContextWindow) {
            this.minContextWindow = minContextWindow;
            return this;
        }

        /**
         * Filter by maximum context window size. Only models with context windows <= this value will be returned.
         *
         * @param maxContextWindow Maximum context window in tokens
         * @return this builder
         */
        public Builder maxContextWindow(Integer maxContextWindow) {
            this.maxContextWindow = maxContextWindow;
            return this;
        }

        /**
         * Filter by model name pattern (regex). Only models whose names match this pattern will be returned.
         *
         * @param namePattern Regular expression pattern for model names
         * @return this builder
         */
        public Builder namePattern(String namePattern) {
            this.namePattern = namePattern;
            return this;
        }

        /**
         * Whether to include deprecated models in results. If false, deprecated models are excluded.
         * If null (default), deprecated status is ignored.
         *
         * @param includeDeprecated true to include deprecated models, false to exclude them, null to ignore
         * @return this builder
         */
        public Builder includeDeprecated(Boolean includeDeprecated) {
            this.includeDeprecated = includeDeprecated;
            return this;
        }

        public ModelDiscoveryFilter build() {
            return new ModelDiscoveryFilter(this);
        }
    }
}
