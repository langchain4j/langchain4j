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

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.singletonMap;

/**
 * Represents a template of a prompt that can be reused multiple times.
 * A template typically contains one or more variables (placeholders) defined as {{variable_name}} that are
 * replaced with actual values to produce a Prompt.
 * Special variables {{current_date}}, {{current_time}}, and {{current_date_time}} are automatically
 * filled with LocalDate.now(), LocalTime.now(), and LocalDateTime.now() respectively.
 * This class uses the Mustache templating engine under the hood, so all Mustache syntax and features are supported.
 */
public class PromptTemplate {

    private static final MustacheFactory MUSTACHE_FACTORY = new DefaultMustacheFactory();

    private final Mustache mustache;
    private final Clock clock;

    public PromptTemplate(String template) {
        this(template, Clock.systemDefaultZone());
    }

    PromptTemplate(String template, Clock clock) {
        StringReader stringReader = new StringReader(ensureNotBlank(template, "template"));
        this.mustache = MUSTACHE_FACTORY.compile(stringReader, "template");
        this.clock = ensureNotNull(clock, "clock");
    }

    /**
     * Applies a value to a template containing a single variable. The single variable should have the name {{it}}.
     *
     * @param value The value that will be injected in place of the {{it}} placeholder in the template.
     * @return A Prompt object where the {{it}} placeholder in the template has been replaced by the provided value.
     */
    public Prompt apply(Object value) {
        return apply(singletonMap("it", value));
    }

    /**
     * Applies multiple values to a template containing multiple variables.
     *
     * @param variables A map of variable names to values that will be injected in place of the corresponding placeholders in the template.
     * @return A Prompt object where the placeholders in the template have been replaced by the provided values.
     */
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
        return new PromptTemplate(template);
    }
}
