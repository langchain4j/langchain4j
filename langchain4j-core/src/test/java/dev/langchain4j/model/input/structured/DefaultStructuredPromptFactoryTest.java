package dev.langchain4j.model.input.structured;

import dev.langchain4j.model.input.Prompt;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unused")
class DefaultStructuredPromptFactoryTest implements WithAssertions {
    @StructuredPrompt("Hello, my name is {{name}}")
    static class Greeting {
        public final String name;

        public Greeting(String name) {
            this.name = name;
        }
    }

    @StructuredPrompt("Hello, my name is {{broken}}")
    static class BrokenPrompt {
        public final String name;

        public BrokenPrompt(String name) {
            this.name = name;
        }
    }

    @Test
    void test() {
        DefaultStructuredPromptFactory factory = new DefaultStructuredPromptFactory();
        Prompt p = factory.toPrompt(new Greeting("Klaus"));
        assertThat(p.text()).isEqualTo("Hello, my name is Klaus");
    }

    @Test
    void bad_input() {
        DefaultStructuredPromptFactory factory = new DefaultStructuredPromptFactory();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> factory.toPrompt(null))
                .withMessage("structuredPrompt cannot be null");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> factory.toPrompt(new Object()))
                .withMessage("java.lang.Object should be annotated with @StructuredPrompt "
                        + "to be used as a structured prompt");
    }

    @Test
    void broken_prompt() {
        DefaultStructuredPromptFactory factory = new DefaultStructuredPromptFactory();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> factory.toPrompt(new BrokenPrompt("Klaus")))
                .withMessage("Value for the variable 'broken' is missing");
    }
}
