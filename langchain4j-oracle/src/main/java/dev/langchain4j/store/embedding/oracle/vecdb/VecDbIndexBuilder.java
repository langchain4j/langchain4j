package dev.langchain4j.store.embedding.oracle.vecdb;

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
    CreateOption createOption = CreateOption.CREATE_NONE;

    VecDbIndexBuilder(VecDbIndexOrganization organization) {
        this.organization = organization;
    }

    public T distanceMetric(VecDbDistanceMetric distanceMetric) {
        this.distanceMetric = ensureNotNull(distanceMetric, "distanceMetric");
        return self();
    }

    public T accuracy(int accuracy) {
        if (accuracy < 1 || accuracy > 100) {
            throw new IllegalArgumentException("accuracy must be between 1 and 100");
        }
        this.accuracy = accuracy;
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

    static int ensurePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than 0");
        }
        return value;
    }
}
