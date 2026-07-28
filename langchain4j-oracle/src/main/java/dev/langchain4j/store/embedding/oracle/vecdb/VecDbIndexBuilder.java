package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbIndexOrganization;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbQuantizationType;

/**
 * Shared base for VecDB vector-index builders.
 *
 * <p>Applications do not create this builder directly. Use {@link VecDbVectorIndex#ivfIndexBuilder()} for an IVF
 * index or {@link VecDbVectorIndex#hnswIndexBuilder()} for an HNSW index. Both concrete builders inherit the common
 * options defined here and add organization-specific parameters.
 *
 * <p>The builder describes the {@code vector_index_params} object passed to
 * {@code DBMS_VECTOR_DATABASE.CREATE_VECTOR_TABLE}, {@code CREATE_INDEX}, or {@code REBUILD_INDEX}. It does not
 * execute database operations. {@link VecDbSchemaManager} applies the resulting configuration when
 * {@link OracleVecDbEmbeddingStore} is built.
 *
 * <p>By default, the index uses {@link VecDbDistanceMetric#COSINE} and
 * {@link CreateOption#CREATE_NONE}. The index metric is independent from the store's search-time metric. Optional
 * properties that are not configured are omitted from the VecDB JSON so the database can apply its defaults.
 *
 * <p>This class uses a self-referential generic type so inherited methods return the concrete IVF or HNSW builder.
 * Consequently, calls such as {@code ivfIndexBuilder().accuracy(90).partitions(100)} remain type-safe and fluent.
 *
 * @param <T> concrete IVF or HNSW builder type returned from common configuration methods
 */
abstract class VecDbIndexBuilder<T extends VecDbIndexBuilder<T>> {

    final VecDbIndexOrganization organization;
    VecDbDistanceMetric distanceMetric = VecDbDistanceMetric.COSINE;
    Integer accuracy;
    VecDbQuantizationType quantizationType;
    Integer compressionRatio;
    Boolean onlineBuild;
    CreateOption createOption = CreateOption.CREATE_NONE;

    /**
     * Creates the shared part of a vector-index builder for a fixed VecDB organization.
     *
     * @param organization physical index organization selected by the concrete builder
     */
    VecDbIndexBuilder(VecDbIndexOrganization organization) {
        this.organization = organization;
    }

    /**
     * Configures the distance function used to build the vector index.
     *
     * <p>The value is written as {@code vector_index_params.distance_metric}. It is independent from the store's
     * search-time metric. When the two metrics differ, Oracle bypasses this index and performs an exact search.
     *
     * @param distanceMetric distance function recommended by the model that generated the stored embeddings
     * @return this concrete builder
     * @throws IllegalArgumentException if {@code distanceMetric} is {@code null}
     */
    public T distanceMetric(VecDbDistanceMetric distanceMetric) {
        this.distanceMetric = ensureNotNull(distanceMetric, "distanceMetric");
        return self();
    }

    /**
     * Configures the target vector-index accuracy.
     *
     * <p>The value is written as {@code vector_index_params.accuracy}. A higher value asks VecDB to favor search
     * accuracy, potentially at the cost of index build time, storage, or query performance.
     *
     * @param accuracy target accuracy from {@code 0} through {@code 100}
     * @return this concrete builder
     * @throws IllegalArgumentException if {@code accuracy} is outside {@code 0..100}
     */
    public T accuracy(int accuracy) {
        this.accuracy = ensureBetween(accuracy, 0, 100, "accuracy");
        return self();
    }

    /**
     * Configures vector-index quantization.
     *
     * <p>The value is written as {@code vector_index_params.quantization_type}. Scalar quantization also requires a
     * {@linkplain #compressionRatio(int) compression ratio}.
     *
     * @param quantizationType quantization mode
     * @return this concrete builder
     * @throws IllegalArgumentException if {@code quantizationType} is {@code null}
     */
    public T quantizationType(VecDbQuantizationType quantizationType) {
        this.quantizationType = ensureNotNull(quantizationType, "quantizationType");
        return self();
    }

    /**
     * Configures the compression ratio for scalar quantization.
     *
     * <p>The value is written as {@code vector_index_params.compression_ratio}. Calling this method requires
     * {@link VecDbQuantizationType#SCALAR}; the relationship is validated by {@link #build()}.
     *
     * @param compressionRatio supported ratio: {@code 2}, {@code 4}, or {@code 8}
     * @return this concrete builder
     * @throws IllegalArgumentException if the value is not {@code 2}, {@code 4}, or {@code 8}
     */
    public T compressionRatio(int compressionRatio) {
        if (compressionRatio != 2 && compressionRatio != 4 && compressionRatio != 8) {
            throw new IllegalArgumentException("compressionRatio must be one of: 2, 4, 8");
        }
        this.compressionRatio = compressionRatio;
        return self();
    }

    /**
     * Configures whether VecDB should build the index online.
     *
     * <p>The value is written as {@code vector_index_params.online_build}. When this method is not called, the
     * property is omitted and the database default applies.
     *
     * @param onlineBuild {@code true} to request an online build; {@code false} to explicitly disable it
     * @return this concrete builder
     */
    public T onlineBuild(boolean onlineBuild) {
        this.onlineBuild = onlineBuild;
        return self();
    }

    /**
     * Configures how schema preparation manages the vector index.
     *
     * <ul>
     *     <li>{@link CreateOption#CREATE_NONE} leaves an existing table's index unchanged and is the default.</li>
     *     <li>{@link CreateOption#CREATE_IF_NOT_EXISTS} creates the index only when it is absent.</li>
     *     <li>{@link CreateOption#CREATE_OR_REPLACE} creates a missing index or rebuilds an existing index.</li>
     * </ul>
     *
     * <p>When the vector table itself is being created, this option also controls the
     * {@code vector_index_params.auto_index} value included in the table-creation request.
     *
     * @param createOption vector-index lifecycle option
     * @return this concrete builder
     * @throws IllegalArgumentException if {@code createOption} is {@code null}
     */
    public T createOption(CreateOption createOption) {
        this.createOption = ensureNotNull(createOption, "createOption");
        return self();
    }

    /**
     * Validates the combined index settings and creates an immutable index configuration.
     *
     * <p>Scalar quantization requires a compression ratio, while a compression ratio cannot be used with another
     * quantization type. An HNSW quantization algorithm likewise requires scalar quantization.
     *
     * @return immutable vector-index configuration
     * @throws IllegalArgumentException if quantization and compression options are inconsistent
     */
    public VecDbVectorIndex build() {
        if (quantizationType == VecDbQuantizationType.SCALAR && compressionRatio == null) {
            throw new IllegalArgumentException("compressionRatio is required for SCALAR quantization");
        }
        if (compressionRatio != null && quantizationType != VecDbQuantizationType.SCALAR) {
            throw new IllegalArgumentException("compressionRatio requires SCALAR quantization");
        }
        if (this instanceof VecDbHnswIndexBuilder hnswBuilder
                && hnswBuilder.quantizationAlgorithm != null
                && quantizationType != VecDbQuantizationType.SCALAR) {
            throw new IllegalArgumentException("quantizationAlgorithm requires SCALAR quantization");
        }
        return new VecDbVectorIndex(this);
    }

    /**
     * Returns this instance as its concrete builder type.
     *
     * @return concrete IVF or HNSW builder
     */
    protected abstract T self();
}
