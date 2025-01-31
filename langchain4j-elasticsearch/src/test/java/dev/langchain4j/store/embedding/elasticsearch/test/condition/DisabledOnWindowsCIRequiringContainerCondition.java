package dev.langchain4j.store.embedding.elasticsearch.test.condition;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.test.condition.AbstractDisabledOnWindowsCICondition;
import org.junit.jupiter.api.extension.ExecutionCondition;

/**
 * A JUnit {@link ExecutionCondition} for disabling tests that are running on a GitHub hosted windows runner.
 * @see DisabledOnWindowsCIRequiringContainer
 */
public final class DisabledOnWindowsCIRequiringContainerCondition
        extends AbstractDisabledOnWindowsCICondition<DisabledOnWindowsCIRequiringContainer> {
    public DisabledOnWindowsCIRequiringContainerCondition() {
        super(DisabledOnWindowsCIRequiringContainer.class);
    }

    @Override
    protected String getDisabledReason(final DisabledOnWindowsCIRequiringContainer annotation) {
        return annotation.disabledReason();
    }

    @Override
    protected boolean shouldBeDisabled() {
        return super.shouldBeDisabled()
                && isNullOrBlank(System.getenv("ELASTICSEARCH_CLOUD_URL"))
                && isNullOrBlank(System.getenv("ELASTICSEARCH_CLOUD_API_KEY"))
                && isNullOrBlank(System.getenv("ELASTICSEARCH_LOCAL_URL"));
    }
}
