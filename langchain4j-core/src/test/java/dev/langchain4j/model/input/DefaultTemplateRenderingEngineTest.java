package dev.langchain4j.model.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.input.DefaultPromptTemplateFactory.DefaultTemplate;
import dev.langchain4j.spi.prompt.Template;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultTemplateRenderingEngineTest {

    private final DefaultTemplateRenderingEngine subject = new DefaultTemplateRenderingEngine();

    @Test
    void should_render_template_with_single_variable() {
        Template template = new DefaultTemplate("Hello, {{name}}!");
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "Alice");

        Prompt result = subject.render(template, variables);

        assertThat(result.text()).isEqualTo("Hello, Alice!");
    }

    @Test
    void should_render_template_with_multiple_variables() {
        Template template = new DefaultTemplate("{{greeting}}, {{name}}!");
        Map<String, Object> variables = new HashMap<>();
        variables.put("greeting", "Hi");
        variables.put("name", "Bob");

        Prompt result = subject.render(template, variables);

        assertThat(result.text()).isEqualTo("Hi, Bob!");
    }

    @Test
    void should_throw_exception_when_variable_is_missing() {
        Template template = new DefaultTemplate("Hello, {{name}}!");

        Map<String, Object> variables = new HashMap<>();

        assertThatThrownBy(() -> subject.render(template, variables))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value for the variable 'name' is missing");
    }

    @Test
    void should_throw_exception_when_variable_value_is_null() {
        Template template = new DefaultTemplate("Hello, {{name}}!");
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", null);

        assertThatThrownBy(() -> subject.render(template, variables))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value for the variable 'name' is null");
    }

    @Test
    void should_throw_exception_if_not_all_variables_are_provided() {
        Template template = new DefaultTemplate("Hello, {{name}}! Welcome to the {{place}}!");

        Map<String, Object> providedVariables = Map.of("name", "Alice");

        assertThatThrownBy(() -> subject.render(template, providedVariables))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value for the variable 'place' is missing");
    }
}
