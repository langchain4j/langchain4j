package dev.langchain4j.observability.event;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@code guardrailName()} on {@link dev.langchain4j.observability.api.event.GuardrailExecutedEvent}.
 * Validates that guardrailName() returns the logical name of the guardrail, not the adapter/wrapper class name.
 *
 * @see <a href="https://github.com/langchain4j/langchain4j/issues/4938">Issue #4938</a>
 */
class GuardrailNameTest {

    private static final InvocationContext INVOCATION_CONTEXT = InvocationContext.builder()
            .interfaceName("TestInterface")
            .methodName("testMethod")
            .chatMemoryId("test-id")
            .build();

    // --- Guardrail name() contract ---

    /** Plain guardrail returns its simple class name by default. */
    @Test
    void plainGuardrail_nameDefaultsToSimpleClassName() {
        assertThat(new PlainInputGuardrail().name()).isEqualTo("PlainInputGuardrail");
        assertThat(new PlainOutputGuardrail().name()).isEqualTo("PlainOutputGuardrail");
    }

    /** Guardrail can override name() to return a custom logical name. */
    @Test
    void guardrail_canOverrideName() {
        assertThat(new CustomNamedInputGuardrail().name()).isEqualTo("PromptInjectionGuardrail");
        assertThat(new CustomNamedOutputGuardrail().name()).isEqualTo("ToxicityGuardrail");
    }

    // --- guardrailName() on event (plain guardrail) ---

    @Test
    void inputGuardrailExecutedEvent_guardrailName_returnsSimpleClassName_whenSetByBuilder() {
        InputGuardrailExecutedEvent event = InputGuardrailExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .guardrailClass(PlainInputGuardrail.class)
                .guardrailName("PlainInputGuardrail")
                .request(request())
                .result(InputGuardrailResult.success())
                .duration(java.time.Duration.ofMillis(10))
                .build();

        assertThat(event.guardrailClass().getSimpleName()).isEqualTo("PlainInputGuardrail");
        assertThat(event.guardrailName()).isEqualTo("PlainInputGuardrail");
    }

    @Test
    void outputGuardrailExecutedEvent_guardrailName_returnsSimpleClassName_whenSetByBuilder() {
        OutputGuardrailExecutedEvent event = OutputGuardrailExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .guardrailClass(PlainOutputGuardrail.class)
                .guardrailName("PlainOutputGuardrail")
                .request(outputRequest())
                .result(OutputGuardrailResult.success())
                .duration(java.time.Duration.ofMillis(10))
                .build();

        assertThat(event.guardrailClass().getSimpleName()).isEqualTo("PlainOutputGuardrail");
        assertThat(event.guardrailName()).isEqualTo("PlainOutputGuardrail");
    }

    // --- guardrailName() falls back to guardrailClass().getSimpleName() when not set ---

    @Test
    void inputGuardrailExecutedEvent_guardrailName_fallsBackToGuardrailClassSimpleName() {
        InputGuardrailExecutedEvent event = InputGuardrailExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .guardrailClass(PlainInputGuardrail.class)
                .request(request())
                .result(InputGuardrailResult.success())
                .duration(java.time.Duration.ofMillis(10))
                .build();

        // guardrailName() not set on builder → falls back to guardrailClass().getSimpleName()
        assertThat(event.guardrailName()).isEqualTo("PlainInputGuardrail");
    }

    @Test
    void outputGuardrailExecutedEvent_guardrailName_fallsBackToGuardrailClassSimpleName() {
        OutputGuardrailExecutedEvent event = OutputGuardrailExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .guardrailClass(PlainOutputGuardrail.class)
                .request(outputRequest())
                .result(OutputGuardrailResult.success())
                .duration(java.time.Duration.ofMillis(10))
                .build();

        assertThat(event.guardrailName()).isEqualTo("PlainOutputGuardrail");
    }

    // --- guardrailName() reflects Guardrail.name() for custom-named guardrails ---

    @Test
    void inputGuardrailExecutedEvent_guardrailName_reflectsCustomGuardrailName() {
        InputGuardrailExecutedEvent event = InputGuardrailExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .guardrailClass(CustomNamedInputGuardrail.class)
                .guardrailName("PromptInjectionGuardrail")
                .request(request())
                .result(InputGuardrailResult.success())
                .duration(java.time.Duration.ofMillis(10))
                .build();

        // guardrailClass() returns CustomNamedInputGuardrail (the adapter/wrapper)
        assertThat(event.guardrailClass().getSimpleName()).isEqualTo("CustomNamedInputGuardrail");
        // guardrailName() returns the logical name set via Guardrail.name()
        assertThat(event.guardrailName()).isEqualTo("PromptInjectionGuardrail");
    }

    @Test
    void outputGuardrailExecutedEvent_guardrailName_reflectsCustomGuardrailName() {
        OutputGuardrailExecutedEvent event = OutputGuardrailExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .guardrailClass(CustomNamedOutputGuardrail.class)
                .guardrailName("ToxicityGuardrail")
                .request(outputRequest())
                .result(OutputGuardrailResult.success())
                .duration(java.time.Duration.ofMillis(10))
                .build();

        assertThat(event.guardrailClass().getSimpleName()).isEqualTo("CustomNamedOutputGuardrail");
        assertThat(event.guardrailName()).isEqualTo("ToxicityGuardrail");
    }

    // --- toBuilder preserves guardrailName ---

    @Test
    void toBuilder_preservesGuardrailName() {
        InputGuardrailExecutedEvent original = InputGuardrailExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .guardrailClass(CustomNamedInputGuardrail.class)
                .guardrailName("PromptInjectionGuardrail")
                .request(request())
                .result(InputGuardrailResult.success())
                .duration(java.time.Duration.ofMillis(10))
                .build();

        InputGuardrailExecutedEvent rebuilt = original.toBuilder().build();

        assertThat(rebuilt.guardrailName()).isEqualTo("PromptInjectionGuardrail");
        assertThat(rebuilt.guardrailClass()).isEqualTo(CustomNamedInputGuardrail.class);
    }

    // ---------------------------------------------------------------------------
    // Helper methods and test guardrail implementations
    // ---------------------------------------------------------------------------

    private static InputGuardrailRequest request() {
        return InputGuardrailRequest.builder()
                .userMessage(UserMessage.from("Hello"))
                .commonParams(GuardrailRequestParams.builder()
                        .userMessageTemplate("")
                        .variables(Map.of())
                        .invocationContext(INVOCATION_CONTEXT)
                        .aiServiceListenerRegistrar(dev.langchain4j.observability.api.AiServiceListenerRegistrar
                                .newInstance())
                        .build())
                .build();
    }

    private static OutputGuardrailRequest outputRequest() {
        return OutputGuardrailRequest.builder()
                .responseFromLLM(dev.langchain4j.model.chat.response.ChatResponse.builder()
                        .aiMessage(dev.langchain4j.data.message.AiMessage.from("Hello"))
                        .build())
                .requestParams(GuardrailRequestParams.builder()
                        .userMessageTemplate("")
                        .variables(Map.of())
                        .invocationContext(INVOCATION_CONTEXT)
                        .aiServiceListenerRegistrar(dev.langchain4j.observability.api.AiServiceListenerRegistrar
                                .newInstance())
                        .build())
                .build();
    }

    // Plain guardrail — name() defaults to simple class name
    static class PlainInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    static class PlainOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(
                dev.langchain4j.data.message.AiMessage responseFromLLM) {
            return success();
        }
    }

    // Guardrail with a custom name (simulating a decorator overriding Guardrail.name())
    static class CustomNamedInputGuardrail implements InputGuardrail {
        @Override
        public String name() {
            return "PromptInjectionGuardrail";
        }

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    static class CustomNamedOutputGuardrail implements OutputGuardrail {
        @Override
        public String name() {
            return "ToxicityGuardrail";
        }

        @Override
        public OutputGuardrailResult validate(
                dev.langchain4j.data.message.AiMessage responseFromLLM) {
            return success();
        }
    }
}
