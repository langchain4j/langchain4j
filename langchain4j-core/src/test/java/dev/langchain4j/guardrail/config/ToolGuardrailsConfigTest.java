package dev.langchain4j.guardrail.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ToolGuardrailsConfigTest {

    @Test
    void should_build_default_tool_input_guardrails_config() {
        assertThat(ToolInputGuardrailsConfig.builder().build())
                .isInstanceOf(GuardrailsConfig.class)
                .returns(true, ToolInputGuardrailsConfig::isArgumentsRewriteNoteEnabled)
                .returns(
                        ToolInputGuardrailsConfig.DEFAULT_ARGUMENTS_REWRITE_NOTE_TEMPLATE,
                        ToolInputGuardrailsConfig::argumentsRewriteNoteTemplate);
    }

    @Test
    void should_build_default_tool_output_guardrails_config() {
        assertThat(ToolOutputGuardrailsConfig.builder().build()).isNotNull().isInstanceOf(GuardrailsConfig.class);
    }

    @Test
    void the_default_note_tag_precedes_the_result() {
        assertThat(ToolInputGuardrailsConfig.DEFAULT_ARGUMENTS_REWRITE_NOTE_TEMPLATE)
                .startsWith("<tool_arguments_changed>")
                .contains(ToolInputGuardrailsConfig.ARGUMENTS_PLACEHOLDER)
                .endsWith("</tool_arguments_changed>\n" + ToolInputGuardrailsConfig.RESULT_PLACEHOLDER);
    }

    @Test
    void should_build_a_tool_input_guardrails_config_with_the_note_disabled_or_another_template() {
        var config = ToolInputGuardrailsConfig.builder()
                .argumentsRewriteNoteEnabled(false)
                .argumentsRewriteNoteTemplate("{{result}}\n(ran with {{arguments}})")
                .build();

        assertThat(config)
                .returns(false, ToolInputGuardrailsConfig::isArgumentsRewriteNoteEnabled)
                .returns("{{result}}\n(ran with {{arguments}})", ToolInputGuardrailsConfig::argumentsRewriteNoteTemplate)
                .isEqualTo(ToolInputGuardrailsConfig.builder()
                        .argumentsRewriteNoteEnabled(false)
                        .argumentsRewriteNoteTemplate("{{result}}\n(ran with {{arguments}})")
                        .build());
    }

    @Test
    void a_template_without_the_result_placeholder_is_rejected() {
        assertThatThrownBy(() -> ToolInputGuardrailsConfig.builder().argumentsRewriteNoteTemplate("changed: {{arguments}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{{result}}");
    }
}
