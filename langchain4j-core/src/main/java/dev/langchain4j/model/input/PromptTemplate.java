package dev.langchain4j.model.input;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import static java.util.Collections.singletonMap;

public class PromptTemplate {

    private static final MustacheFactory MUSTACHE_FACTORY = new DefaultMustacheFactory();

    private final Mustache mustache;

    public PromptTemplate(String template) {
        this.mustache = MUSTACHE_FACTORY.compile(new StringReader(template), "template");
    }

    public Prompt apply(Object value) {
        return apply(singletonMap("it", value));
    }

    public Prompt apply(Map<String, Object> variables) {
        StringWriter writer = new StringWriter();
        mustache.execute(writer, variables);
        return Prompt.from(writer.toString());
    }

    public static PromptTemplate from(String template) {
        return new PromptTemplate(template);
    }
}
