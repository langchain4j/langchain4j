package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbIndexOrganization;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbQuantizationType;

/**
 * Common configuration for VecDB index builders.
 *
 * @param <T> concrete builder type
 */
abstract class VecDbIndexBuilder<T extends VecDbIndexBuilder<T>> {

    final VecDbIndexOrganization organization;
    VecDbDistanceMetric distanceMetric = VecDbDistanceMetric.COSINE;
    Integer accuracy;
    VecDbQuantizationType quantizationType;
    Integer compressionRatio;
    Boolean onlineBuild;
    CreateOption createOption = CreateOption.CREATE_NONE;

    VecDbIndexBuilder(VecDbIndexOrganization organization) {
        this.organization = organization;
    }

    public T distanceMetric(VecDbDistanceMetric distanceMetric) {
        this.distanceMetric = ensureNotNull(distanceMetric, "distanceMetric");
        return self();
    }

    public T accuracy(int accuracy) {
        this.accuracy = ensureBetween(accuracy, 0, 100, "accuracy");
        return self();
    }

    public T quantizationType(VecDbQuantizationType quantizationType) {
        this.quantizationType = ensureNotNull(quantizationType, "quantizationType");
        return self();
    }

    public T compressionRatio(int compressionRatio) {
        if (compressionRatio != 2 && compressionRatio != 4 && compressionRatio != 8) {
            throw new IllegalArgumentException("compressionRatio must be one of: 2, 4, 8");
        }
        this.compressionRatio = compressionRatio;
        return self();
    }

    public T onlineBuild(boolean onlineBuild) {
        this.onlineBuild = onlineBuild;
        return self();
    }

    public T createOption(CreateOption createOption) {
        this.createOption = ensureNotNull(createOption, "createOption");
        return self();
    }

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

    protected abstract T self();
}
