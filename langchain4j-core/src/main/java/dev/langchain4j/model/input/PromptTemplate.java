package dev.langchain4j.model.input;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.Collections.singletonMap;

public class PromptTemplate {

    private static final MustacheFactory MUSTACHE_FACTORY = new DefaultMustacheFactory();

    private final Mustache mustache;

    public PromptTemplate(String template) {
        if (isNullOrBlank(template)) {
            throw illegalArgument("Prompt template cannot be null or empty");
        }
        this.mustache = MUSTACHE_FACTORY.compile(new StringReader(template), "template");
    }

    public Prompt apply(Object value) {
        return apply(singletonMap("it", value));
    }

    public Prompt apply(Map<String, Object> variables) {
        StringWriter writer = new StringWriter();
        mustache.execute(writer, injectDateTimeVariables(variables));
        return Prompt.from(writer.toString());
    }

    private static Map<String, Object> injectDateTimeVariables(Map<String, Object> variables) {
        Map<String, Object> variablesCopy = new HashMap<>(variables);
        variablesCopy.put("current_date", LocalDate.now());
        variablesCopy.put("current_time", LocalTime.now());
        variablesCopy.put("current_date_time", LocalDateTime.now());
        return variablesCopy;
    }

    public static PromptTemplate from(String template) {
        return new PromptTemplate(template);
    }
}
