package dev.langchain4j.model;

import org.assertj.core.api.ThrowableAssert;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public abstract class DisabledModelTest<T> {
    private final Class<T> modelClass;

    protected DisabledModelTest(Class<T> modelClass) {
        this.modelClass = modelClass;
    }

    protected void performAssertion(ThrowableAssert.ThrowingCallable throwingCallable) {
        assertThatExceptionOfType(ModelDisabledException.class)
                .isThrownBy(throwingCallable)
                .withMessage("%s is disabled", this.modelClass.getSimpleName());
    }
}
