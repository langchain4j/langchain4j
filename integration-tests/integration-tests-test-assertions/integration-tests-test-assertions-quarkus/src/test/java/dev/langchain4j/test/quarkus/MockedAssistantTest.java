package dev.langchain4j.test.quarkus;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;

import com.example.support.CustomerSupportAssistant;
import com.example.support.SupportService;
import dev.langchain4j.test.service.AiServiceCapture;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Replacing the AI Service itself with a mock: nothing is captured, because no AI Service created by LangChain4j runs.
 */
@QuarkusTest
class MockedAssistantTest {

    @Inject
    SupportService supportService;

    @BeforeEach
    void mockAssistant() {
        QuarkusMock.installMockForType(
                new CustomerSupportAssistant() {

                    @Override
                    public String chat(String message) {
                        return "Mocked answer";
                    }

                    @Override
                    public CompletableFuture<String> chatAsync(String message) {
                        return CompletableFuture.completedFuture("Mocked answer");
                    }

                    @Override
                    public Flow.Publisher<String> chatStream(String message) {
                        throw new UnsupportedOperationException();
                    }
                },
                CustomerSupportAssistant.class);
    }

    @Test
    void records_nothing_when_ai_service_is_mocked() {
        AiServiceCapture invocations = AiServiceCapture.capture(() -> supportService.answer("Hi"));

        assertThat(invocations.invocations()).isEmpty();
        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("No AI Service invocation was captured.")
                .hasMessageContaining("the AI Service is a mock");
    }

    @Test
    void records_nothing_when_mocked_ai_service_returns_uni() {
        AiServiceCapture invocations = AiServiceCapture.capture(() -> supportService.answerLater("Hi"));

        assertThat(invocations.invocations()).isEmpty();
    }

    @Test
    void capture_expecting_failure_explains_why_nothing_was_recorded() {
        assertThatThrownBy(() -> AiServiceCapture.captureExpectingFailure(() -> supportService.answer("Hi")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected an AI Service invocation to fail")
                .hasMessageContaining("No AI Service invocation was captured")
                .hasMessageContaining("mock");
    }

    @Test
    void hint_for_mocked_ai_service_mentions_quarkus_mocks() {
        AiServiceCapture invocations = AiServiceCapture.capture(() -> supportService.answer("Hi"));

        assertThatThrownBy(invocations::invocation).hasMessageContaining("QuarkusMock");
    }

    @Test
    void lambda_output_is_the_answer_of_the_mock_although_nothing_is_recorded() {
        AiServiceCapture capture = AiServiceCapture.capture(() -> supportService.answer("Hi"));

        String answer = capture.lambdaOutput(String.class);
        assertThat(answer).isEqualTo("Mocked answer");
        assertThat(capture.invocations()).isEmpty();
        assertThatThrownBy(capture::output)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("QuarkusMock");
        assertThatThrownBy(capture::outputText)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("QuarkusMock");
    }
}
