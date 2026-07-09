package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration of metadata indexes managed by {@code DBMS_VECTOR_DATABASE}.
 *
 * <p>Automatic indexing discovers qualifying scalar metadata paths. Explicit include and exclude paths refine which
 * metadata paths are indexed.
 */
public final class VecDbMetadataIndex {

    private final boolean autoIndex;
    private final List<String> includePaths;
    private final List<String> excludePaths;
    private final CreateOption createOption;

    private VecDbMetadataIndex(Builder builder) {
        this.autoIndex = builder.autoIndex;
        this.includePaths = List.copyOf(builder.includePaths);
        this.excludePaths = List.copyOf(builder.excludePaths);
        this.createOption = builder.createOption;
    }

    /** Returns a builder for metadata-index configuration. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns whether VecDB should automatically discover and maintain qualifying metadata paths. */
    public boolean autoIndex() {
        return autoIndex;
    }

    /** Returns metadata paths that should be included in indexing. */
    public List<String> includePaths() {
        return includePaths;
    }

    /** Returns metadata paths that should be excluded from indexing. */
    public List<String> excludePaths() {
        return excludePaths;
    }

    /** Returns the lifecycle option for this metadata-index configuration. */
    public CreateOption createOption() {
        return createOption;
    }

    /** Builder for {@link VecDbMetadataIndex}. */
    public static final class Builder {

        private boolean autoIndex = true;
        private final List<String> includePaths = new ArrayList<>();
        private final List<String> excludePaths = new ArrayList<>();
        private CreateOption createOption = CreateOption.CREATE_NONE;

        private Builder() {}

        /**
         * Configures automatic discovery and maintenance of qualifying metadata paths. The default is {@code true}.
         */
        public Builder autoIndex(boolean autoIndex) {
            this.autoIndex = autoIndex;
            return this;
        }

        /** Adds a metadata path that should be indexed. */
        public Builder includePath(String path) {
            includePaths.add(ensureNotBlank(path, "includePath"));
            return this;
        }

        /** Adds a metadata path that should not be indexed. */
        public Builder excludePath(String path) {
            excludePaths.add(ensureNotBlank(path, "excludePath"));
            return this;
        }

        /** Configures whether metadata indexes are created, reused, or rebuilt. */
        public Builder createOption(CreateOption createOption) {
            this.createOption = ensureNotNull(createOption, "createOption");
            return this;
        }

        /** Builds the metadata-index configuration. */
        public VecDbMetadataIndex build() {
            if (includePaths.contains("*") && excludePaths.contains("*")) {
                throw new IllegalArgumentException(
                        "includePaths and excludePaths cannot both contain the wildcard \"*\"");
            }
            return new VecDbMetadataIndex(this);
        }
    }
}
