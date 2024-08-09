package dev.langchain4j.spi.prompt;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class PromptTemplateFactoryTest implements WithAssertions {
    @Test
    public void test_Input_defaults() {
        PromptTemplateFactory.Input input = () -> "template";
        assertThat(input.getName()).isEqualTo("template");
    }

    @Test
    public void test_template() {
        PromptTemplateFactory.Template template = variables -> "rendered template";
        assertThat(template.render(new HashMap<>())).isEqualTo("rendered template");
        assertThat(template.getAllVariables()).isEmpty();
    }
}