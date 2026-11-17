package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.test.service.AiServiceCapture;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Captures the invocations of an {@code @AiService} made by another Spring bean that the test calls.
 */
@SpringBootTest
@Import(TestModels.class)
class SupportServiceCaptureTest {

    @Autowired
    SupportService supportService;

    @Test
    void captures_the_ai_service_invoked_by_a_service_bean() {
        AiServiceCapture capture = capture(() -> supportService.answer("customer-10", "  Please cancel MS-501  "));

        assertThat(capture)
                .hasToolExecutedWith("getBookingDetails", Map.of("bookingNumber", "MS-501"))
                .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-501"))
                .hasNoFailedToolExecutions()
                .hasNoMoreToolExecutions();
        assertThat(capture.invocation().invocationContext().interfaceName())
                .isEqualTo(SupportAssistant.class.getName());
        assertThat(capture.firstRequest()).hasUserMessageContaining("Please cancel MS-501");
        assertThat(capture.lambdaOutput()).isEqualTo(capture.outputText());
    }

    @Test
    void captures_each_ai_service_invocation_of_a_service_bean() {
        AiServiceCapture capture = capture(() -> {
            supportService.answer("customer-11", "Hi");
            return supportService.answer("customer-11", "What is your return policy?");
        });

        assertThat(capture.invocations()).hasSize(2);
        assertThat(capture.invocations().get(0)).hasNoRetrievedSources();
        assertThat(capture.invocations().get(1)).hasRetrievedSourceContaining("within 14 days");
        assertThatThrownBy(capture::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected a single top-level AI Service invocation, but 2 were captured");
    }
}
