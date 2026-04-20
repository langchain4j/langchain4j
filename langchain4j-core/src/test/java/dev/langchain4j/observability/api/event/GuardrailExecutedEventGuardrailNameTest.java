package dev.langchain4j.observability.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GuardrailExecutedEvent#guardrailName()} to verify that decorators
 * expose the logical name of the wrapped guardrail rather than the decorator class name.
 */
class GuardrailExecutedEventGuardrailNameTest {

    @Test
    void guardrailName_should_return_simple_class_name_by_default() {
        // given
        var event = InputGuardrailExecutedEvent.builder()
                .guardrailClass(MyGuardrail.class)
                .build();

        // when
        String name = event.guardrailName();

        // then
        assertThat(name).isEqualTo("MyGuardrail");
    }

    @Test
    void guardrailName_should_return_decorator_overridden_name() {
        // given
        var event = InputGuardrailExecutedEvent.builder()
                .guardrailClass(MyGuardrailDecorator.class)
                .guardrailName("MyLogicalGuardrail")
                .build();

        // when
        String name = event.guardrailName();

        // then
        assertThat(name).isEqualTo("MyLogicalGuardrail");
    }

    // --- Test guardrail classes ---

    private static class MyGuardrail implements InputGuardrail {}

    private static class MyGuardrailDecorator implements InputGuardrail {
        @Override
        public String name() {
            return "MyLogicalGuardrail";
        }
    }
}
