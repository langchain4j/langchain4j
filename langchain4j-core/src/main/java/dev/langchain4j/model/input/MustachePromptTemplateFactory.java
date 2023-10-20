package dev.langchain4j.model.input;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import dev.langchain4j.spi.prompt.PromptTemplateFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

class MustachePromptTemplateFactory implements PromptTemplateFactory {

    private static final MustacheFactory MUSTACHE_FACTORY = new DefaultMustacheFactory();

    @Override
    public MustacheTemplate create(PromptTemplateFactory.Input input) {
        StringReader stringReader = new StringReader(input.getTemplate());
        return new MustacheTemplate(MUSTACHE_FACTORY.compile(stringReader, input.getName()));
    }

    static class MustacheTemplate implements Template {

        private final Mustache mustache;

        MustacheTemplate(Mustache mustache) {
            this.mustache = mustache;
        }

        public String render(Map<String, Object> vars) {
            StringWriter writer = new StringWriter();
            mustache.execute(writer, vars);
            return writer.toString();
        }
    }
}
