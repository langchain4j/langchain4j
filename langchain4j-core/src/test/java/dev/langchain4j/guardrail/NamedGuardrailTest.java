package dev.langchain4j.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.AbstractGuardrailExecutor.NamedGuardrail;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.listener.InputGuardrailExecutedListener;
import dev.langchain4j.test.guardrail.GuardrailAssertions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NamedGuardrail} support in guardrail events.
 * Verifies that guardrailName() returns the logical name when guardrails implement
 * NamedGuardrail, and falls back to class simple name otherwise.
 */
class NamedGuardrailTest {

    private static final InvocationContext DEFAULT_INVOCATION_CONTEXT = InvocationContext.builder()
            .interfaceName("SomeInterface")
            .methodName("someMethod")
            .methodArgument("one")
            .methodArgument("two")
            .chatMemoryId("one")
            .build();

    @Test
    void guardrailName_shouldReturnLogicalName_whenGuardrailImplementsNamedGuardrail() {
        // Given
        var recordedEvents = new CopyOnWriteArrayList<InputGuardrailExecutedEvent>();
        var registrar = AiServiceListenerRegistrar.newInstance();
        registrar.register((InputGuardrailExecutedListener) recordedEvents::add);

        var namedGuardrail = new NamedInputGuardrail("MyCustomGuardrail");
        var request = fromWithRegistrar(UserMessage.from("test"), registrar);
        var executor = InputGuardrailExecutor.builder().guardrails(namedGuardrail).build();

        // When
        var result = executor.execute(request);

        // Then
        GuardrailAssertions.assertThat(result).isSuccessful();
        assertThat(recordedEvents).hasSize(1);
        assertThat(recordedEvents.get(0).guardrailName()).isEqualTo("MyCustomGuardrail");
        assertThat(recordedEvents.get(0).guardrailClass().getSimpleName()).isEqualTo("NamedInputGuardrail");
    }

    @Test
    void guardrailName_shouldReturnClassName_whenGuardrailDoesNotImplementNamedGuardrail() {
        // Given
        var recordedEvents = new CopyOnWriteArrayList<InputGuardrailExecutedEvent>();
        var registrar = AiServiceListenerRegistrar.newInstance();
        registrar.register((InputGuardrailExecutedListener) recordedEvents::add);

        var simpleGuardrail = new SimpleInputGuardrail();
        var request = fromWithRegistrar(UserMessage.from("test"), registrar);
        var executor = InputGuardrailExecutor.builder().guardrails(simpleGuardrail).build();

        // When
        var result = executor.execute(request);

        // Then
        GuardrailAssertions.assertThat(result).isSuccessful();
        assertThat(recordedEvents).hasSize(1);
        assertThat(recordedEvents.get(0).guardrailName()).isEqualTo("SimpleInputGuardrail");
    }

    @Test
    void guardrailName_shouldDelegateToWrappedGuardrail_whenDecoratorImplementsNamedGuardrail() {
        // Given
        var recordedEvents = new CopyOnWriteArrayList<InputGuardrailExecutedEvent>();
        var registrar = AiServiceListenerRegistrar.newInstance();
        registrar.register((InputGuardrailExecutedListener) recordedEvents::add);

        var namedGuardrail = new NamedInputGuardrail("OriginalGuardrail");
        var delegatingGuardrail = new DelegatingInputGuardrail(namedGuardrail);
        var request = fromWithRegistrar(UserMessage.from("test"), registrar);
        var executor = InputGuardrailExecutor.builder().guardrails(delegatingGuardrail).build();

        // When
        var result = executor.execute(request);

        // Then
        GuardrailAssertions.assertThat(result).isSuccessful();
        assertThat(recordedEvents).hasSize(1);
        // The delegating guardrail's guardrailName() delegates to the wrapped guardrail
        assertThat(recordedEvents.get(0).guardrailName()).isEqualTo("OriginalGuardrail");
        // But guardrailClass() returns the actual class
        assertThat(recordedEvents.get(0).guardrailClass().getSimpleName()).isEqualTo("DelegatingInputGuardrail");
    }

    public static InputGuardrailRequest from(UserMessage userMessage) {
        return fromWithRegistrar(userMessage, AiServiceListenerRegistrar.newInstance());
    }

    private static InputGuardrailRequest fromWithRegistrar(UserMessage userMessage, AiServiceListenerRegistrar registrar) {
        var newCommonParams = GuardrailRequestParams.builder()
                .chatMemory(null)
                .augmentationResult(null)
                .userMessageTemplate("")
                .variables(Map.of())
                .invocationContext(DEFAULT_INVOCATION_CONTEXT)
                .aiServiceListenerRegistrar(registrar)
                .build();

        return InputGuardrailRequest.builder()
                .userMessage(userMessage)
                .commonParams(newCommonParams)
                .build();
    }

    /**
     * A guardrail that implements {@link NamedGuardrail} with a custom name.
     */
    static class NamedInputGuardrail implements InputGuardrail, NamedGuardrail<InputGuardrailRequest, InputGuardrailResult> {
        private final String name;

        NamedInputGuardrail(String name) {
            this.name = name;
        }

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return InputGuardrailResult.success();
        }

        @Override
        public String getName() {
            return name;
        }
    }

    /**
     * A simple guardrail without {@link NamedGuardrail} implementation.
     */
    static class SimpleInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return InputGuardrailResult.success();
        }
    }

    /**
     * A delegating guardrail that wraps another guardrail.
     * This simulates the adapter/decorator pattern where the wrapper class
     * should delegate to the wrapped guardrail's name via NamedGuardrail.
     */
    static class DelegatingInputGuardrail implements InputGuardrail, NamedGuardrail<InputGuardrailRequest, InputGuardrailResult> {
        private final InputGuardrail wrapped;

        DelegatingInputGuardrail(InputGuardrail wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return wrapped.validate(userMessage);
        }

        @Override
        public String getName() {
            if (wrapped instanceof NamedGuardrail) {
                return ((NamedGuardrail<InputGuardrailRequest, InputGuardrailResult>) wrapped).getName();
            }
            return wrapped.getClass().getSimpleName();
        }
    }
}
