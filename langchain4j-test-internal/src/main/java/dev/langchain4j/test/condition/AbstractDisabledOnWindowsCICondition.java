package dev.langchain4j.test.condition;

import java.lang.annotation.Annotation;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;

/**
 * A JUnit {@link ExecutionCondition} for disabling tests that are running on a GitHub hosted windows runner.
 */
public abstract class AbstractDisabledOnWindowsCICondition<A extends Annotation> implements ExecutionCondition {
    private final Class<A> annotationType;

    public AbstractDisabledOnWindowsCICondition(final Class<A> annotationType) {
        this.annotationType = annotationType;
    }

    protected abstract String getDisabledReason(A annotation);

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
        return AnnotationUtils.findAnnotation(context.getElement(), this.annotationType)
                .map(this::evaluateExecutionCondition)
                .orElseGet(this::enabledByDefault);
    }

    protected ConditionEvaluationResult evaluateExecutionCondition(final A annotation) {
        return shouldBeDisabled()
                ? ConditionEvaluationResult.disabled(getDisabledReason(annotation))
                : ConditionEvaluationResult.enabled(
                        "Enabled on operating system %s or not running on a GitHub hosted Windows runner"
                                .formatted(OS.current()));
    }

    protected boolean shouldBeDisabled() {
        return isOnWindows() && isOnGithubActionCi();
    }

    protected boolean isOnWindows() {
        return OS.current() == OS.WINDOWS;
    }

    protected boolean isOnGithubActionCi() {
        return Boolean.parseBoolean(System.getenv("IS_GH_ACTION_CI"));
    }

    protected ConditionEvaluationResult enabledByDefault() {
        return ConditionEvaluationResult.enabled("@%s is not present".formatted(this.annotationType.getSimpleName()));
    }
}
