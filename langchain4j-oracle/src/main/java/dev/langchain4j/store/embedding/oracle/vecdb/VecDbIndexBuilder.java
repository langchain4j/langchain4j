package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.store.embedding.oracle.CreateOption;

/**
 * Common configuration for VecDB index builders.
 *
 * @param <T> concrete builder type
 */
abstract class VecDbIndexBuilder<T extends VecDbIndexBuilder<T>> {

    final VecDbIndexOrganization organization;
    VecDbDistanceMetric distanceMetric = VecDbDistanceMetric.COSINE;
    Integer accuracy;
    Integer parallelCreation;
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

    public T parallelCreation(int parallelCreation) {
        this.parallelCreation = ensureGreaterThanZero(parallelCreation, "parallelCreation");
        return self();
    }

    public T createOption(CreateOption createOption) {
        this.createOption = ensureNotNull(createOption, "createOption");
        return self();
    }

    public VecDbIndex build() {
        return new VecDbIndex(this);
    }

    protected abstract T self();
}
