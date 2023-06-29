package dev.langchain4j.model.input;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateTest {

    @Test
    void should_create_prompt_from_template_with_single_variable() {

        PromptTemplate promptTemplate = new PromptTemplate("My name is {{it}}.");

        Prompt prompt = promptTemplate.apply("Klaus");

        assertThat(prompt.text()).isEqualTo("My name is Klaus.");
    }

    @Test
    void should_create_prompt_from_template_with_multiple_variables() {

        PromptTemplate promptTemplate = new PromptTemplate("My name is {{name}} {{surname}}.");

        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "Klaus");
        variables.put("surname", "Heißler");


        Prompt prompt = promptTemplate.apply(variables);


        assertThat(prompt.text()).isEqualTo("My name is Klaus Heißler.");
    }
}