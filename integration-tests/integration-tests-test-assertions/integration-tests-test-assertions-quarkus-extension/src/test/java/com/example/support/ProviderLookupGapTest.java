package com.example.support;

import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * What users of a Quarkus extension version that does not pass the {@code ObservabilityListenersProvider}s of the
 * application to LangChain4j get when they capture its AI Services.
 */
@QuarkusTest
class ProviderLookupGapTest {

    @Inject
    SupportAssistant supportAssistant;

    @Test
    void tells_that_the_ai_services_of_the_extension_cannot_be_captured() {
        // Quarkus extension does not pass the providers of the application to LangChain4j yet (ISSUE-PROVIDER-LOOKUP, to be opened); delete this test when it does
        assertThatThrownBy(() -> capture(() -> supportAssistant.chat("customer-1", "Hi")).invocation())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ExtensionCapture.NOT_LISTENING)
                .hasMessageContaining(ExtensionCapture.FRAMEWORK_CAUSE);
    }
}
