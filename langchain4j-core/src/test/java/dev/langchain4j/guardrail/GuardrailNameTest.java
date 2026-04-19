package dev.langchain4j.guardrail;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static dev.langchain4j.guardrail.GuardrailRequestParams.builder;

/**
 * Regression tests for guardrailName() — verifies that Guardrail.name() and
 * GuardrailExecutedEvent.guardrailName() return the logical guardrail name,
 * not the adapter/wrapper class name when decorators wrap guardrails.
 */
class GuardrailNameTest {

    private static final InvocationContext INVOCATION_CONTEXT = InvocationContext.builder()
            .interfaceName("TestInterface")
            .methodName("testMethod")
            .build();

    private static final GuardrailRequestParams REQUEST_PARAMS = builder()
            .userMessageTemplate("")
            .variables(Map.of())
            .invocationContext(INVOCATION_CONTEXT)
            .build();

    // --- Guardrail.name() tests ---

    @Test
    void guardrail_name_should_return_simpleClassName_by_default() {
        Guardrail<? super InputGuardrailRequest, ? extends InputGuardrailResult> guardrail =
            new TestInputGuardrail("TestInputGuardrail");
        assertThat(guardrail.name()).isEqualTo("TestInputGuardrail");
    }

    @Test
    void guardrailDecorator_should_override_name_to_return_wrapped_guardrail_name() {
        Guardrail<? super InputGuardrailRequest, ? extends InputGuardrailResult> inner =
            new TestInputGuardrail("InnerGuardrail");
        Guardrail<? super InputGuardrailRequest, ? extends InputGuardrailResult> decorator =
            new TestInputGuardrail("DecoratorGuardrail") {
                @Override
                public String name() {
                    return inner.name();
                }
            };
        assertThat(decorator.name()).isEqualTo("InnerGuardrail");
    }

    // --- GuardrailExecutedEvent.guardrailName() tests ---

    @Test
    void guardrailExecutedEvent_guardrailName_should_fall_back_to_guardrailClass_simpleName() {
        InputGuardrailExecutedEvent event = InputGuardrailExecutedEvent.builder()
            .request(InputGuardrailRequest.builder()
                .userMessage(UserMessage.from("hello"))
                .commonParams(REQUEST_PARAMS)
                .build())
            .result(InputGuardrailResult.success())
            .invocationContext(INVOCATION_CONTEXT)
            .guardrailClass(TestInputGuardrail.class)
            .duration(Duration.ZERO)
            .build();
        assertThat(event.guardrailClass()).isEqualTo(TestInputGuardrail.class);
        assertThat(event.guardrailName()).isEqualTo("TestInputGuardrail");
    }

    @Test
    void guardrailExecutedEvent_withExplicitGuardrailName_should_return_it() {
        InputGuardrailExecutedEvent event = InputGuardrailExecutedEvent.builder()
            .request(InputGuardrailRequest.builder()
                .userMessage(UserMessage.from("hello"))
                .commonParams(REQUEST_PARAMS)
                .build())
            .result(InputGuardrailResult.success())
            .invocationContext(INVOCATION_CONTEXT)
            .guardrailClass(DecoratorInputGuardrail.class)
            .guardrailName("InnerGuardrail")
            .duration(Duration.ZERO)
            .build();

        assertThat(event.guardrailClass()).isEqualTo(DecoratorInputGuardrail.class);
        assertThat(event.guardrailName()).isEqualTo("InnerGuardrail");
    }

    @Test
    void guardrailExecutedEventBuilder_copies_guardrailName() {
        InputGuardrailExecutedEvent original = InputGuardrailExecutedEvent.builder()
            .request(InputGuardrailRequest.builder()
                .userMessage(UserMessage.from("hello"))
                .commonParams(REQUEST_PARAMS)
                .build())
            .result(InputGuardrailResult.success())
            .invocationContext(INVOCATION_CONTEXT)
            .guardrailClass(TestInputGuardrail.class)
            .guardrailName("LogicalName")
            .duration(Duration.ZERO)
            .build();

        InputGuardrailExecutedEvent copied = InputGuardrailExecutedEvent.builder()
            .guardrailClass(original.guardrailClass())
            .guardrailName(original.guardrailName())
            .request(original.request())
            .result(original.result())
            .invocationContext(original.request().requestParams().invocationContext())
            .duration(Duration.ZERO)
            .build();

        assertThat(copied.guardrailName()).isEqualTo("LogicalName");
    }

    // --- Test fixtures ---

    private static class TestInputGuardrail implements InputGuardrail {
        private final String name;

        TestInputGuardrail(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return InputGuardrailResult.success();
        }
    }

    private static class DecoratorInputGuardrail implements InputGuardrail {
        @Override
        public String name() {
            return "InnerGuardrail";
        }

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return InputGuardrailResult.success();
        }
    }
}
