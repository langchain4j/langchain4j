package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.test.service.AiServiceCapture;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Captures the invocations of a {@code @RegisterAiService} made by another CDI bean that the test calls.
 */
@QuarkusTest
class SupportServiceCaptureTest {

    @Inject
    SupportService supportService;

    @Test
    void captures_the_ai_service_invoked_by_a_service_bean() {
        // Quarkus extension does not pass the providers of the application to LangChain4j yet (ISSUE-PROVIDER-LOOKUP, to be opened); flip this assert when it does, by calling captures_the_ai_service_invoked_by_a_service_beanOnceCaptured() directly
        ExtensionCapture.assertNotCaptured(this::captures_the_ai_service_invoked_by_a_service_beanOnceCaptured);
    }

    private void captures_the_ai_service_invoked_by_a_service_beanOnceCaptured() {
        AiServiceCapture capture = capture(() -> supportService.answer("customer-10", "  Please cancel MS-501  "));

        assertThat(capture).executedToolNames().containsExactly("getBookingDetails", "cancelBooking");
        // Quarkus extension does not report ToolExecutedEvent.isError() in the pinned version (fixed on quarkus-langchain4j main: https://github.com/quarkiverse/quarkus-langchain4j/pull/3102); flip this assert when the pin moves to a release that contains it
        assertThatThrownBy(() -> assertThat(capture)
                        .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-501")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("the AI Service did not report whether its tool executions failed");
        assertThat(capture.invocation().invocationContext().interfaceName())
                .isEqualTo(SupportAssistant.class.getName());
        assertThat(capture.firstRequest()).hasUserMessageContaining("Please cancel MS-501");
        assertThat(capture.lambdaOutput()).isEqualTo(capture.outputText());
    }

    @Test
    void captures_each_ai_service_invocation_of_a_service_bean() {
        // Quarkus extension does not pass the providers of the application to LangChain4j yet (ISSUE-PROVIDER-LOOKUP, to be opened); flip this assert when it does, by calling captures_each_ai_service_invocation_of_a_service_beanOnceCaptured() directly
        ExtensionCapture.assertNotCaptured(() -> capture(() -> supportService.answer("customer-11", "Hi"))
                .invocation());
    }

    private void captures_each_ai_service_invocation_of_a_service_beanOnceCaptured() {
        AiServiceCapture capture = capture(() -> {
            supportService.answer("customer-11", "Hi");
            return supportService.answer("customer-11", "Please cancel MS-502");
        });

        assertThat(capture.invocations()).hasSize(2);
        assertThat(capture.invocations().get(0)).hasNoToolExecutions();
        assertThat(capture.invocations().get(1)).executedToolNames().contains("cancelBooking");
        assertThatThrownBy(capture::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected a single top-level AI Service invocation, but 2 were captured");
    }
}
