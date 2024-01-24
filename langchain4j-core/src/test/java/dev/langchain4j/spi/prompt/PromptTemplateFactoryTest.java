package dev.langchain4j.spi.prompt;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class PromptTemplateFactoryTest implements WithAssertions {
    @Test
    public void test_Input_defaults() {
        PromptTemplateFactory.Input input = () -> "template";
        assertThat(input.getName()).isEqualTo("template");
    }

}