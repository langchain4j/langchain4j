package dev.langchain4j.model.input;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PromptTemplateTest {

    @Test
    void should_create_prompt_from_template_without_variables() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("Hello world.");

        Map<String, Object> variables = emptyMap();

        // when
        Prompt prompt = promptTemplate.apply(variables);

        // then
        assertThat(prompt.text()).isEqualTo("Hello world.");
    }

    @Test
    void should_create_prompt_from_template_with_it_variable() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("My name is {{it}}.");

        // when
        Prompt prompt = promptTemplate.apply("Klaus");

        // then
        assertThat(prompt.text()).isEqualTo("My name is Klaus.");
    }

    @Test
    void should_create_prompt_from_template_with_single_variable() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("My name is {{name}}.");

        Map<String, Object> variables = singletonMap("name", "Klaus");

        // when
        Prompt prompt = promptTemplate.apply(variables);

        // then
        assertThat(prompt.text()).isEqualTo("My name is Klaus.");
    }

    @Test
    void should_support_spaces_inside_double_curly_brackets() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("My name is {{ name }}.");

        Map<String, Object> variables = singletonMap("name", "Klaus");

        // when
        Prompt prompt = promptTemplate.apply(variables);

        // then
        assertThat(prompt.text()).isEqualTo("My name is Klaus.");
    }

    @Test
    void should_create_prompt_from_template_with_multiple_variables() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("My name is {{name}}, I am {{age}} years old.");

        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "Klaus");
        variables.put("age", 42);

        // when
        Prompt prompt = promptTemplate.apply(variables);

        // then
        assertThat(prompt.text()).isEqualTo("My name is Klaus, I am 42 years old.");
    }

    @Test
    void should_allow_same_variable_in_template_multiple_times() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("My name is {{name}}, call me {{name}}.");

        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "Klaus");

        // when
        Prompt prompt = promptTemplate.apply(variables);

        // then
        assertThat(prompt.text()).isEqualTo("My name is Klaus, call me Klaus.");
    }

    @Test
    void should_fail_when_value_is_missing() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("My name is {{name}}.");

        Map<String, Object> variables = emptyMap();

        // when-then
        assertThatThrownBy(() -> promptTemplate.apply(variables))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Value for the variable 'name' is missing");
    }

    @Test
    void should_fail_when_value_is_null() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("My name is {{name}}.");

        Map<String, Object> variables = new HashMap<>();
        variables.put("name", null);

        // when-then
        assertThatThrownBy(() -> promptTemplate.apply(variables))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Value for the variable 'name' is null");
    }

    @Test
    void should_provide_date_automatically() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("Today is {{current_date}}");

        Map<String, Object> variables = emptyMap();

        // when
        Prompt prompt = promptTemplate.apply(variables);

        // then
        assertThat(prompt.text()).isEqualTo("Today is " + LocalDate.now());
    }

    @Test
    void should_provide_time_automatically() {

        // given
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);

        PromptTemplate promptTemplate = PromptTemplate.from("My name is {{it}} and now is {{current_time}}", clock);

        // when
        Prompt prompt = promptTemplate.apply("Klaus");

        // then
        assertThat(prompt.text()).isEqualTo("My name is Klaus and now is " + LocalTime.now(clock));
    }

    @Test
    void should_provide_date_and_time_automatically() {

        // given
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);

        PromptTemplate promptTemplate =
                PromptTemplate.from("My name is {{name}} and now is {{current_date_time}}", clock);

        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "Klaus");

        // when
        Prompt prompt = promptTemplate.apply(variables);

        // then
        assertThat(prompt.text()).isEqualTo("My name is Klaus and now is " + LocalDateTime.now(clock));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "$",
                "$$",
                "{",
                "{{",
                "}",
                "}}",
                "{}",
                "{{}}",
                "*",
                "**",
                "\\",
                "\\\\",
                "${}*\\",
                "${ *hello* }",
                "\\$\\{ \\*hello\\* \\}"
            })
    void should_support_special_characters(String s) {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("This is {{it}}.");

        // when
        Prompt prompt = promptTemplate.apply(s);

        // then
        assertThat(prompt.text()).isEqualTo("This is " + s + ".");
    }

    @Test
    void should_preserve_non_text_content_inside_template_variable() {

        ImageContent image = new ImageContent("https://example.org/picture.png");
        Prompt prompt = PromptTemplate.from("Describe {{it}} for me.").apply(Map.of("it", image));

        assertThat(prompt.contents())
                .containsExactly(TextContent.from("Describe "), image, TextContent.from(" for me."));

        assertThatThrownBy(prompt::text).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_expand_variable_whose_value_is_list_of_content() {

        ImageContent first = new ImageContent("https://example.org/a.png");
        ImageContent second = new ImageContent("https://example.org/b.png");

        Prompt prompt =
                PromptTemplate.from("Compare {{pair}} side by side.").apply(Map.of("pair", List.of(first, second)));

        assertThat(prompt.contents())
                .containsExactly(TextContent.from("Compare "), first, second, TextContent.from(" side by side."));

        assertThatThrownBy(prompt::text).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_merge_scalar_substitutions_with_surrounding_literals_like_render() {

        Prompt prompt = PromptTemplate.from("{{a}}{{b}}.").apply(Map.of("a", "Hello", "b", " Klaus"));

        assertThat(prompt.text()).isEqualTo("Hello Klaus.");
        assertThat(prompt.contents()).singleElement().isInstanceOf(TextContent.class);
    }
}
