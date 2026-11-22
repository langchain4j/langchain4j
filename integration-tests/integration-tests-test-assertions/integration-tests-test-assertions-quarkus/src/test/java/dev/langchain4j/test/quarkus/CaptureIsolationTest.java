package dev.langchain4j.test.quarkus;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.quarkus.Models.useChatModelResponding;

import com.example.support.CustomerSupportAssistant;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CaptureIsolationTest {

    private static final AtomicReference<Object> FIRST_ASSISTANT_INSTANCE = new AtomicReference<>();

    @Inject
    CustomerSupportAssistant assistant;

    @Test
    @Order(1)
    void first_test_records_its_invocations() {
        FIRST_ASSISTANT_INSTANCE.set(ClientProxy.unwrap(assistant));
        useChatModelResponding(AiMessage.from("first"), AiMessage.from("second"));

        AiServiceCapture invocations = AiServiceCapture.capture(() -> {
            assistant.chat("one");
            assistant.chat("two");
        });

        assertThat(invocations.invocations()).hasSize(2);
    }

    @Test
    @Order(2)
    void second_test_does_not_see_invocations_of_first_test() {
        assertThat(ClientProxy.unwrap(assistant))
                .as("the application-scoped AI Service is reused between tests")
                .isSameAs(FIRST_ASSISTANT_INSTANCE.get());
        useChatModelResponding(AiMessage.from("third"));

        AiServiceInvocation invocation =
                AiServiceCapture.capture(() -> assistant.chat("three")).invocation();

        assertThat(invocation.outputText()).isEqualTo("third");
    }

    @Test
    @Order(3)
    void invocation_outside_of_capture_is_not_recorded() {
        useChatModelResponding(AiMessage.from("before"), AiMessage.from("inside"), AiMessage.from("after"));

        assistant.chat("before");
        AiServiceCapture invocations = AiServiceCapture.capture(() -> assistant.chat("inside"));
        assistant.chat("after");

        assertThat(invocations.outputText()).isEqualTo("inside");
    }

    @Test
    @Order(4)
    void recording_without_ai_service_call_is_empty() {
        AiServiceCapture invocations = AiServiceCapture.capture(() -> "no AI Service here");

        assertThat(invocations.invocations()).isEmpty();
        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("No AI Service invocation was captured.");
    }

    @Test
    @Order(5)
    void does_not_capture_invocation_started_on_another_thread() {
        useChatModelResponding(AiMessage.from("from another thread"));

        AiServiceCapture invocations = AiServiceCapture.capture(
                () -> CompletableFuture.supplyAsync(() -> assistant.chat("Hi")).join());

        assertThat(invocations.invocations()).isEmpty();
        assertThatThrownBy(invocations::invocation).hasMessageContaining("an executor");
    }

    @Test
    @Order(6)
    void nested_recordings_on_the_same_thread() {
        useChatModelResponding(AiMessage.from("outer"), AiMessage.from("inner"));

        AtomicReference<AiServiceCapture> inner = new AtomicReference<>();
        AiServiceCapture outer = AiServiceCapture.capture(() -> {
            assistant.chat("outer");
            inner.set(AiServiceCapture.capture(() -> assistant.chat("inner")));
        });

        assertThat(inner.get().outputText()).isEqualTo("inner");
        assertThat(outer.outputText()).isEqualTo("outer");
    }
}
