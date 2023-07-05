package dev.langchain4j.model.input;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    @Test
    void should_provide_date_automatically() {

        PromptTemplate promptTemplate = new PromptTemplate("My name is {{it}} and today is {{current_date}}");

        Prompt prompt = promptTemplate.apply("Klaus");

        assertThat(prompt.text()).isEqualTo("My name is Klaus and today is " + LocalDate.now());
    }

    @Test
    void should_provide_time_automatically() {

        PromptTemplate promptTemplate = new PromptTemplate("My name is {{it}} and now is {{current_time}}");

        Prompt prompt = promptTemplate.apply("Klaus");

        assertThat(prompt.text()).isEqualTo("My name is Klaus and now is " + LocalTime.now());
    }

    @Test
    void should_provide_date_and_time_automatically() {

        PromptTemplate promptTemplate = new PromptTemplate("My name is {{it}} and now is {{current_date_time}}");

        Prompt prompt = promptTemplate.apply("Klaus");

        assertThat(prompt.text()).isEqualTo("My name is Klaus and now is " + LocalDateTime.now());
    }
}