package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class GuardrailServiceTargetClassTest {

    interface MyAiService {}

    /**
     * An implementation written before {@code targetClass()} existed: it only knows {@code aiServiceClass()}.
     */
    @SuppressWarnings("deprecation")
    private static final class LegacyGuardrailService implements GuardrailService {
        @Override
        public Class<?> aiServiceClass() {
            return MyAiService.class;
        }

        @Override
        public <MethodKey> InputGuardrailResult executeInputGuardrails(
                MethodKey method, InputGuardrailRequest request) {
            return InputGuardrailResult.success();
        }

        @Override
        public <MethodKey> OutputGuardrailResult executeOutputGuardrails(
                MethodKey method, OutputGuardrailRequest request) {
            return OutputGuardrailResult.success();
        }

        @Override
        public <MethodKey> boolean hasInputGuardrails(MethodKey method) {
            return false;
        }

        @Override
        public <MethodKey> boolean hasOutputGuardrails(MethodKey method) {
            return false;
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void an_implementation_that_only_knows_ai_service_class_gets_a_target_class() {
        var service = new LegacyGuardrailService();

        assertThat(service.targetClass()).isSameAs(MyAiService.class).isSameAs(service.aiServiceClass());
    }

    @Test
    @SuppressWarnings("deprecation")
    void the_default_service_reports_the_ai_service_as_its_target_and_through_the_generic_contract() {
        var service =
                new DefaultAiServiceGuardrailService(MyAiService.class, Map.of(), Map.of(), UnaryOperator.identity());
        GenericGuardrailService<
                        InputGuardrailRequest, InputGuardrailResult, OutputGuardrailRequest, OutputGuardrailResult>
                generic = service;

        assertThat(generic.targetClass()).isSameAs(MyAiService.class);
        assertThat(service.aiServiceClass()).isSameAs(MyAiService.class);
        assertThat(generic.hasInputGuardrails("anyKey")).isFalse();
        assertThat(generic.hasOutputGuardrails(null)).isFalse();
    }
}
