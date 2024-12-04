package dev.langchain4j.model.input;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.spi.prompt.PromptTemplateFactory;
import dev.langchain4j.spi.prompt.Template;
import dev.langchain4j.spi.prompt.TemplateRenderingEngine;
import java.util.Map;

class DefaultPromptTemplateFactory implements PromptTemplateFactory {

    private static final TemplateRenderingEngine RENDERING_ENGINE = new DefaultTemplateRenderingEngine();

    @Override
    public DefaultTemplate create(PromptTemplateFactory.Input input) {
        return new DefaultTemplate(input.getTemplate());
    }

    static class DefaultTemplate implements Template {

        private final String content;

        public DefaultTemplate(String template) {
            this.content = ensureNotBlank(template, "template");
        }

        @Override
        public String content() {
            return content;
        }

        @Override
        public String render(Map<String, Object> variables) {
            return RENDERING_ENGINE.render(this, variables).text();
        }
    }
}
