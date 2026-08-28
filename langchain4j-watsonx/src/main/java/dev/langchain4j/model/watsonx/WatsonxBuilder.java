package dev.langchain4j.model.watsonx;

import dev.langchain4j.Internal;

/**
 * Abstract builder for the watsonx.ai services that operate inside a project or a deployment space.
 *
 * @param <T> the concrete builder subclass
 */
@Internal
@SuppressWarnings("unchecked")
abstract class WatsonxBuilder<T extends WatsonxBuilder<T>> extends WatsonxConnectionBuilder<T> {

    protected String projectId;
    protected String spaceId;

    /**
     * Sets the IBM Cloud project ID that owns the watsonx.ai resources.
     * Exactly one of {@code projectId} or {@code spaceId} must be set.
     *
     * @param projectId the IBM Cloud project ID
     * @return {@code this}
     */
    public T projectId(String projectId) {
        this.projectId = projectId;
        return (T) this;
    }

    /**
     * Sets the IBM Cloud deployment space ID.
     * Exactly one of {@code projectId} or {@code spaceId} must be set.
     *
     * @param spaceId the IBM Cloud deployment space ID
     * @return {@code this}
     */
    public T spaceId(String spaceId) {
        this.spaceId = spaceId;
        return (T) this;
    }
}
