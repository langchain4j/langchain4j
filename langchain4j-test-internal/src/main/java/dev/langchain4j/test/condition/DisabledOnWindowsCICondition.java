package dev.langchain4j.test.condition;

import org.junit.jupiter.api.extension.ExecutionCondition;

/**
 * A JUnit {@link ExecutionCondition} for disabling tests that are running on a GitHub hosted windows runner.
 * @see DisabledOnWindowsCI
 */
public final class DisabledOnWindowsCICondition extends AbstractDisabledOnWindowsCICondition<DisabledOnWindowsCI> {
    public DisabledOnWindowsCICondition() {
        super(DisabledOnWindowsCI.class);
    }

    @Override
    protected String getDisabledReason(final DisabledOnWindowsCI annotation) {
        return annotation.disabledReason();
    }
}
