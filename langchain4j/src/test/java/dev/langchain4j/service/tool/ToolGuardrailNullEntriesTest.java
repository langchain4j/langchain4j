package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.service.AiServices;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * A {@code null} tool guardrail, or guardrail class, among the ones given to a setter fails right away, with a message
 * that names the setting and the position of the {@code null}.
 */
@SuppressWarnings("unchecked")
class ToolGuardrailNullEntriesTest {

    interface Assistant {

        String chat(String message);
    }

    public static class AcceptingInput implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return success();
        }
    }

    public static class AcceptingOutput implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            return success();
        }
    }

    private static final ToolInputGuardrail INPUT = new AcceptingInput();
    private static final ToolOutputGuardrail OUTPUT = new AcceptingOutput();

    @Test
    void a_null_guardrail_of_an_ai_service_tool_fails_naming_the_setting_and_its_position() {
        var builder = AiServiceTool.builder();

        assertThatThrownBy(() -> builder.toolInputGuardrails(INPUT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolInputGuardrails[1] cannot be null");
        assertThatThrownBy(() -> builder.toolInputGuardrails(Arrays.asList(null, INPUT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolInputGuardrails[0] cannot be null");
        assertThatThrownBy(() -> builder.toolOutputGuardrails(OUTPUT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolOutputGuardrails[1] cannot be null");
        assertThatThrownBy(() -> builder.toolOutputGuardrails(Arrays.asList(null, OUTPUT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolOutputGuardrails[0] cannot be null");
    }

    @Test
    void a_null_tool_guardrail_given_to_ai_services_fails_naming_the_setting_and_its_position() {
        var builder = AiServices.builder(Assistant.class);

        assertThatThrownBy(() -> builder.toolInputGuardrails(INPUT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolInputGuardrails[1] cannot be null");
        assertThatThrownBy(() -> builder.toolInputGuardrails(Arrays.asList(null, INPUT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolInputGuardrails[0] cannot be null");
        assertThatThrownBy(() -> builder.toolOutputGuardrails(OUTPUT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolOutputGuardrails[1] cannot be null");
        assertThatThrownBy(() -> builder.toolOutputGuardrails(Arrays.asList(null, OUTPUT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolOutputGuardrails[0] cannot be null");
    }

    @Test
    void a_null_tool_guardrail_class_given_to_ai_services_fails_naming_the_setting_and_its_position() {
        var builder = AiServices.builder(Assistant.class);

        assertThatThrownBy(() -> builder.toolInputGuardrailClasses(AcceptingInput.class, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolInputGuardrailClasses[1] cannot be null");
        assertThatThrownBy(() -> builder.toolInputGuardrailClasses(Arrays.asList(null, AcceptingInput.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolInputGuardrailClasses[0] cannot be null");
        assertThatThrownBy(() -> builder.toolOutputGuardrailClasses(AcceptingOutput.class, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolOutputGuardrailClasses[1] cannot be null");
        assertThatThrownBy(() -> builder.toolOutputGuardrailClasses(Arrays.asList(null, AcceptingOutput.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolOutputGuardrailClasses[0] cannot be null");
    }
}
