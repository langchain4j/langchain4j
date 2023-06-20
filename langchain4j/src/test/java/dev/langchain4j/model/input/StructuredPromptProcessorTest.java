package dev.langchain4j.model.input;

import dev.langchain4j.model.input.structured.StructuredPrompt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class StructuredPromptProcessorTest {

    @StructuredPrompt("Hello, my name is {{name}}")
    static class Greeting {

        private String name;
    }

    @Test
    void test_prompt_with_single_variable() {
        Greeting structuredPrompt = new Greeting();
        structuredPrompt.name = "Klaus";

        Prompt prompt = toPrompt(structuredPrompt);

        assertThat(prompt.text()).isEqualTo("Hello, my name is Klaus");
    }

    @StructuredPrompt({
            "Suggest tasty {{dish}} recipes that can be prepared in {{maxPreparationTime}} minutes.",
            "I have only {{ingredients}} in my fridge."
    })
    static class SuggestRecipes {

        private String dish;
        private int maxPreparationTime;
        private List<String> ingredients;
    }

    @Test
    void test_prompt_with_multiple_variables() {
        SuggestRecipes structuredPrompt = new SuggestRecipes();
        structuredPrompt.dish = "salad";
        structuredPrompt.maxPreparationTime = 5;
        structuredPrompt.ingredients = asList("Tomato", "Cucumber", "Onion");

        Prompt prompt = toPrompt(structuredPrompt);

        assertThat(prompt.text())
                .isEqualTo("Suggest tasty salad recipes that can be prepared in 5.0 minutes.\nI have only [Tomato, Cucumber, Onion] in my fridge.");
    }
}