package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.support.assistant.SupportAssistant;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * A different Spring context than the other tests (because of the property), plus a second application started by
 * the test itself: AI Service beans of all contexts report to the same recorder, which separates them by thread.
 */
@SpringBootTest(properties = "spring.application.name=second-context")
@Import(TestModels.class)
class MultipleContextsTest {

    static ConfigurableApplicationContext otherApplication;

    @Autowired
    SupportAssistant supportAssistant;

    @BeforeAll
    static void startOtherApplication() {
        otherApplication = new SpringApplicationBuilder(SupportApplication.class, TestModels.class)
                .web(WebApplicationType.NONE)
                .run();
    }

    @AfterAll
    static void stopOtherApplication() {
        otherApplication.close();
    }

    @Test
    void the_ai_services_of_two_contexts_are_different_beans() {
        assertThat(otherApplication.getBean(SupportAssistant.class)).isNotSameAs(supportAssistant);
    }

    @Test
    void records_only_the_context_called_on_the_capture_thread() throws Exception {
        SupportAssistant otherAssistant = otherApplication.getBean(SupportAssistant.class);
        CountDownLatch capture = new CountDownLatch(1);
        CompletableFuture<String> otherCall = CompletableFuture.supplyAsync(() -> {
            await(capture);
            return otherAssistant.chat("Please cancel MS-OTHER");
        });

        AiServiceInvocation invocation = capture(() -> {
                    capture.countDown();
                    String answer = supportAssistant.chat("Please cancel MS-THIS");
                    otherCall.join();
                    return answer;
                })
                .invocation();

        assertThat(invocation)
                .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-THIS"))
                .hasToolsExecutedExactlyInAnyOrder("getBookingDetails", "cancelBooking");
        assertThat(otherCall.get(10, TimeUnit.SECONDS)).isEqualTo("Done: Booking MS-OTHER cancelled");
    }

    @Test
    void records_the_ai_services_of_both_contexts_called_on_the_capture_thread() {
        SupportAssistant otherAssistant = otherApplication.getBean(SupportAssistant.class);

        AiServiceCapture invocations = capture(() -> {
            supportAssistant.chat("Please cancel MS-A");
            return otherAssistant.chat("Please refund MS-B 20");
        });

        assertThat(invocations.invocations()).hasSize(2);
        assertThat(invocations.invocations().get(0)).hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-A"));
        assertThat(invocations.invocations().get(1))
                .hasToolExecutedWith("issueRefund", Map.of("bookingNumber", "MS-B", "amountInEuros", 20));
        assertThat(invocations.invocations().get(0).invocationContext().invocationId()).isNotEqualTo(invocations.invocations().get(1).invocationContext().invocationId());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
