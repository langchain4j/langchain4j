package dev.langchain4j.spi.prompt;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.TextContent;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class PromptTemplateFactoryTest implements WithAssertions {
    @Test
    void input_defaults() {
        PromptTemplateFactory.Input input = () -> "template";
        assertThat(input.getName()).isEqualTo("template");
    }

    @Test
    void template_renderContents_default_wraps_render_output_as_text() {
        PromptTemplateFactory.Template template = variables -> "Hello world.";

        List<dev.langchain4j.data.message.Content> contents = template.renderContents(emptyMap());

        assertThat(contents).containsExactly(TextContent.from("Hello world."));
    }
}
