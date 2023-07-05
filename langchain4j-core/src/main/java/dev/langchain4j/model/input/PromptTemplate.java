package dev.langchain4j.model.input;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Clock;
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
    private final Clock clock;

    private PromptTemplate(Mustache mustache, Clock clock) {
        this.mustache = mustache;
        this.clock = clock;
    }

    public Prompt apply(Object value) {
        return apply(singletonMap("it", value));
    }

    public Prompt apply(Map<String, Object> variables) {
        StringWriter writer = new StringWriter();
        mustache.execute(writer, injectDateTimeVariables(variables));
        return Prompt.from(writer.toString());
    }

    private Map<String, Object> injectDateTimeVariables(Map<String, Object> variables) {
        Map<String, Object> variablesCopy = new HashMap<>(variables);
        variablesCopy.put("current_date", LocalDate.now(clock));
        variablesCopy.put("current_time", LocalTime.now(clock));
        variablesCopy.put("current_date_time", LocalDateTime.now(clock));
        return variablesCopy;
    }

    public static PromptTemplate from(String template) {
        return from(template, Clock.systemDefaultZone());
    }

    public static PromptTemplate from(String template, Clock clock) {
        if (isNullOrBlank(template)) {
            throw illegalArgument("Prompt template cannot be null or empty");
        }
        return new PromptTemplate(
                MUSTACHE_FACTORY.compile(new StringReader(template), "template"),
                clock
        );
    }
}
