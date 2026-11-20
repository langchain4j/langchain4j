package dev.langchain4j.guardrail.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolGuardrailsConfigTest {

    @Test
    void should_build_default_tool_input_guardrails_config() {
        assertThat(ToolInputGuardrailsConfig.builder().build()).isNotNull().isInstanceOf(GuardrailsConfig.class);
    }

    @Test
    void should_build_default_tool_output_guardrails_config() {
        assertThat(ToolOutputGuardrailsConfig.builder().build()).isNotNull().isInstanceOf(GuardrailsConfig.class);
    }
}
