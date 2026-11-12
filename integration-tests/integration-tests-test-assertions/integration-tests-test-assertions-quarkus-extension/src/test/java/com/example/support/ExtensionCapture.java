package com.example.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * What capture tells today about the AI Services of the Quarkus extension version these tests use: the extension
 * does not pass the {@code ObservabilityListenersProvider}s of the application to LangChain4j yet
 * (ISSUE-PROVIDER-LOOKUP, to be opened), so none of its AI Service invocations is captured.
 */
final class ExtensionCapture {

    static final String NOT_LISTENING = "No AI Service in this JVM has the listeners of langchain4j-test registered";

    static final String FRAMEWORK_CAUSE = "loads the application with class loaders of its own";

    private ExtensionCapture() {}

    static void assertNotCaptured(ThrowingCallable testOnceCaptured) {
        assertThatThrownBy(testOnceCaptured)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(NOT_LISTENING)
                .hasMessageContaining(FRAMEWORK_CAUSE);
    }
}
