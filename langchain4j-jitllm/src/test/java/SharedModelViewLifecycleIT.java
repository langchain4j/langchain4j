import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.jitllm.JitLLMChatModel;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * The fixture lifecycle the inherited suites depend on.
 *
 * <p>Every case here is about who owns the model, because that is what broke: the suites hand one
 * static model to {@code @ParameterizedTest} cases whose framework closes {@code AutoCloseable}
 * arguments after each invocation, so the first case closed the shared model and the rest failed.
 *
 * <p>Ordered deliberately: the closing case has to run last, because it is the end of this model.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SharedModelViewLifecycleIT {

    private static JitLLMChatModel realModel;

    private static JitLLMChatModel model() {
        if (realModel == null) {
            realModel = JitLLMChatModel.builder()
                    .modelPath(TestModelPath.fromEnvironment())
                    .temperature(0.0)
                    .topP(1.0)
                    .maxTokens(512)
                    .seed(12345)
                    .onGPU(Boolean.TRUE)
                    .build();
        }
        return realModel;
    }

    private static ChatRequest ask(String text) {
        return ChatRequest.builder().messages(UserMessage.from(text)).build();
    }

    /** JUnit closes AutoCloseable arguments; it must not be able to see one here. */
    @Test
    @Order(1)
    void theSuppliedArgumentIsNotAutoCloseable() {
        ChatModel supplied = SharedModelView.of(model());
        assertThat(supplied).isNotInstanceOf(AutoCloseable.class);
        // And the real model still is: production semantics are untouched.
        assertThat(model()).isInstanceOf(AutoCloseable.class);
    }

    /**
     * Several inherited-style invocations against the shared model, each through its own view — the
     * shape a parameterized suite produces.
     */
    @Test
    @Order(2)
    void repeatedInvocationsReuseTheOneUnderlyingModel() {
        for (int invocation = 1; invocation <= 3; invocation++) {
            ChatModel supplied = SharedModelView.of(model()); // a fresh view each time
            ChatResponse response = supplied.chat(ask("Say the word 'ready'."));
            assertThat(response.aiMessage().text())
                    .as("invocation %d must still work; a closed model would throw here", invocation)
                    .isNotBlank();
        }
    }

    /** The underlying model survives everything above, which is the whole point. */
    @Test
    @Order(3)
    void theUnderlyingModelRemainsUsableAfterEarlierInvocations() {
        assertThat(model().chat(ask("Say the word 'still'.")).aiMessage().text())
                .isNotBlank();
    }

    /**
     * Cleanup closes it once, and use afterwards fails — the integration's existing contract, which
     * this arrangement preserves rather than softens.
     */
    @Test
    @Order(4)
    void suiteCleanupClosesItOnceAndUseAfterwardsFails() {
        JitLLMChatModel closing = model();
        closing.close();
        closing.close(); // idempotent: a second close is not an error

        assertThatThrownBy(() -> closing.chat(ask("anything")))
                .as("use after close must fail; close() means what it says")
                .isInstanceOf(RuntimeException.class);

        realModel = null;
    }
}
