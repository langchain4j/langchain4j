package dev.langchain4j.model.input.structured;

import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.input.Prompt;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuredPromptProcessorTest {

    @StructuredPrompt("Hello, my name is {{name}}")
    record Greeting(String name) {}

    @Test
    void prompt_with_single_variable() {
        Greeting structuredPrompt = new Greeting("Klaus");

        Prompt prompt = toPrompt(structuredPrompt);

        assertThat(prompt.text()).isEqualTo("Hello, my name is Klaus");
    }

    @StructuredPrompt({
        "Suggest tasty {{dish}} recipes that can be prepared in {{maxPreparationTime}} minutes.",
        "I have only {{ingredients}} in my fridge.",
    })
    static class SuggestRecipes {

        private String dish;
        private int maxPreparationTime;
        private List<String> ingredients;
    }

    @Test
    void prompt_with_multiple_variables() {
        SuggestRecipes structuredPrompt = new SuggestRecipes();
        structuredPrompt.dish = "salad";
        structuredPrompt.maxPreparationTime = 5;
        structuredPrompt.ingredients = asList("Tomato", "Cucumber", "Onion");

        Prompt prompt = toPrompt(structuredPrompt);

        assertThat(prompt.text())
                .isEqualTo(
                        "Suggest tasty salad recipes that can be prepared in 5 minutes.\nI have only [Tomato, Cucumber, Onion] in my fridge.");
    }

    @StructuredPrompt(
            "Example of numbers with floating point: {{nDouble}}, {{nFloat}} and whole numbers: {{nInt}}, {{nShort}}, {{nLong}}")
    static class VariousNumbers {

        private double nDouble;
        private float nFloat;
        private int nInt;
        private short nShort;
        private long nLong;
    }

    @Test
    void prompt_with_various_number_types() {
        VariousNumbers numbers = new VariousNumbers();
        numbers.nDouble = 17.15;
        numbers.nFloat = 1;
        numbers.nInt = 2;
        numbers.nShort = 10;
        numbers.nLong = 12;

        Prompt prompt = toPrompt(numbers);

        assertThat(prompt.text())
                .isEqualTo("Example of numbers with floating point: 17.15, 1.0 and whole numbers: 2, 10, 12");
    }
}
