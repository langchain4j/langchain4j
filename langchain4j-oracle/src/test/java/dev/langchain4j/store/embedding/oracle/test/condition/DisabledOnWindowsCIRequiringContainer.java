package dev.langchain4j.store.embedding.oracle.test.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Custom JUnit annotation to decide whether a test (or test class) should be disabled when running on windows CI <strong>AND</strong> needs a container.
 * <p>
 *     This is important because windows GitHub hosted runners don't support linux containers,
 *     so any tests which require a windows container will need to be disabled when running on a windows runner.
 * </p>
 * @see DisabledOnWindowsCIRequiringContainerCondition
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(DisabledOnWindowsCIRequiringContainerCondition.class)
public @interface DisabledOnWindowsCIRequiringContainer {
    /**
     * Custom reason to provide if the test is disabled.
     */
    String disabledReason() default
            "Disabled because test is running on Windows on CI. GitHub hosted CI runners don't support linux containers.";
}
