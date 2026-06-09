package dev.langchain4j.guardrail;

import static dev.langchain4j.test.guardrail.GuardrailAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.DefaultAiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.listener.InputGuardrailExecutedListener;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for issue #4938: {@link GuardrailExecutedEvent#guardrailName()}
 * must surface the logical guardrail identity exposed by {@link Guardrail#name()},
 * not the adapter / wrapper class. This keeps observability systems and audit logs
 * meaningful when guardrails are wrapped using the decorator pattern.
 */
class GuardrailExecutedEventGuardrailNameTests {

    private static final InvocationContext DEFAULT_INVOCATION_CONTEXT = InvocationContext.builder()
            .interfaceName("SomeInterface")
            .methodName("someMethod")
            .chatMemoryId("one")
            .build();

    @Test
    void shouldExposeGuardrailClassSimpleNameWhenGuardrailDoesNotOverrideName() {
        // Plain guardrail — no decorator wrapping. The event's guardrailName() must
        // match the guardrail's actual class simple name.
        CapturingListener listener = new CapturingListener();
        DefaultAiServiceListenerRegistrar registrar = new DefaultAiServiceListenerRegistrar();
        registrar.register(listener);

        InputGuardrailRequest request = requestFor(UserMessage.from("hello"), registrar);
        InputGuardrailExecutor executor = InputGuardrailExecutor.builder()
                .config(defaultConfig())
                .guardrails(ProfanityFilterGuardrail.INSTANCE)
                .build();

        executor.execute(request);

        InputGuardrailExecutedEvent event = listener.lastEvent();
        assertThat(event).as("listener should have captured the executed event").isNotNull();
        assertThat(event.guardrailClass().getSimpleName())
                .as("class should still be the actual guardrail class")
                .isEqualTo("ProfanityFilterGuardrail");
        assertThat(event.guardrailName())
                .as("default name() returns the class simple name")
                .isEqualTo("ProfanityFilterGuardrail");
    }

    @Test
    void shouldExposeLogicalNameFromDecoratorWrappedGuardrail() {
        // A decorator / wrapper guardrail. Its getClass() is the adapter class
        // (LoggingDecorator), but its name() returns the wrapped guardrail's
        // logical name. The event must surface the logical name, not the adapter
        // class — this is the bug from issue #4938.
        CapturingListener listener = new CapturingListener();
        DefaultAiServiceListenerRegistrar registrar = new DefaultAiServiceListenerRegistrar();
        registrar.register(listener);

        InputGuardrailRequest request = requestFor(UserMessage.from("hello"), registrar);
        InputGuardrailExecutor executor = InputGuardrailExecutor.builder()
                .config(defaultConfig())
                .guardrails(LoggingDecorator.wrap(ProfanityFilterGuardrail.INSTANCE))
                .build();

        executor.execute(request);

        InputGuardrailExecutedEvent event = listener.lastEvent();
        assertThat(event).isNotNull();
        assertThat(event.guardrailClass().getSimpleName())
                .as("guardrailClass() still reports the decorator's runtime class")
                .isEqualTo("LoggingDecorator");
        assertThat(event.guardrailName())
                .as("guardrailName() must report the underlying guardrail's logical name")
                .isEqualTo("ProfanityFilterGuardrail");
    }

    @Test
    void guardrailNameDefaultsToClassSimpleNameInBuilderWithoutExplicitValue() {
        // When the executor does not call guardrailName(...) on the builder, the
        // event must still report a sensible value derived from guardrailClass().
        // This preserves backward compatibility for any third party that constructs
        // GuardrailExecutedEvent directly.
        InputGuardrailExecutedEvent event = InputGuardrailExecutedEvent.builder()
                .request(requestFor(UserMessage.from("hi"), new DefaultAiServiceListenerRegistrar()))
                .result(InputGuardrailResult.success())
                .guardrailClass(ProfanityFilterGuardrail.class)
                .invocationContext(DEFAULT_INVOCATION_CONTEXT)
                .duration(java.time.Duration.ZERO)
                .build();

        assertThat(event.guardrailName()).isEqualTo("ProfanityFilterGuardrail");
    }

    // -------- helpers --------

    private static InputGuardrailRequest requestFor(UserMessage userMessage, DefaultAiServiceListenerRegistrar registrar) {
        GuardrailRequestParams params = GuardrailRequestParams.builder()
                .chatMemory(null)
                .augmentationResult(null)
                .userMessageTemplate("")
                .variables(Map.of())
                .invocationContext(DEFAULT_INVOCATION_CONTEXT)
                .aiServiceListenerRegistrar(registrar)
                .build();
        return InputGuardrailRequest.builder()
                .userMessage(userMessage)
                .commonParams(params)
                .build();
    }

    private static InputGuardrailsConfig defaultConfig() {
        return InputGuardrailsConfig.builder().build();
    }

    private static final class CapturingListener implements InputGuardrailExecutedListener {
        private final AtomicReference<InputGuardrailExecutedEvent> last = new AtomicReference<>();

        @Override
        public void onEvent(InputGuardrailExecutedEvent event) {
            last.set(event);
        }

        InputGuardrailExecutedEvent lastEvent() {
            return last.get();
        }
    }

    // -------- test guardrails --------

    /** Plain guardrail with no name() override. {@code name()} falls back to the class simple name. */
    public static final class ProfanityFilterGuardrail implements InputGuardrail {
        static final ProfanityFilterGuardrail INSTANCE = new ProfanityFilterGuardrail();

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return InputGuardrailResult.success();
        }
    }

    /**
     * Decorator that wraps a delegate guardrail. Its getClass() is "LoggingDecorator",
     * which would leak through to observability systems under the old behavior.
     * Overrides {@code name()} to return the delegate's logical name.
     */
    public static final class LoggingDecorator implements InputGuardrail {
        private final InputGuardrail delegate;

        private LoggingDecorator(InputGuardrail delegate) {
            this.delegate = delegate;
        }

        static LoggingDecorator wrap(InputGuardrail delegate) {
            return new LoggingDecorator(delegate);
        }

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return delegate.validate(userMessage);
        }

        @Override
        public String name() {
            // Propagate the underlying guardrail's identity to observability.
            return delegate.name();
        }
    }
}
