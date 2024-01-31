package dev.langchain4j.model.input;

import dev.langchain4j.spi.ServiceHelper;
import dev.langchain4j.spi.prompt.PromptTemplateFactory;

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
 */
public class PromptTemplate {

    private static final PromptTemplateFactory FACTORY = ServiceHelper.loadService(
            PromptTemplateFactory.class, DefaultPromptTemplateFactory::new);

    static final String CURRENT_DATE = "current_date";
    static final String CURRENT_TIME = "current_time";
    static final String CURRENT_DATE_TIME = "current_date_time";

    private final String templateString;
    private final PromptTemplateFactory.Template template;
    private final Clock clock;

    /**
     * Create a new PromptTemplate.
     *
     * <p>The {@code Clock} will be the system clock.</p>
     *
     * @param template the template string of the prompt.
     */
    public PromptTemplate(String template) {
        this(template, Clock.systemDefaultZone());
    }

    /**
     * Create a new PromptTemplate.
     * @param template the template string of the prompt.
     * @param clock the clock to use for the special variables.
     */
    PromptTemplate(String template, Clock clock) {
        this.templateString = ensureNotBlank(template, "template");
        this.template = FACTORY.create(() -> template);
        this.clock = ensureNotNull(clock, "clock");
    }

    /**
     * @return A prompt template string.
     */
    public String template() {
        return templateString;
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
        ensureNotNull(variables, "variables");
        return Prompt.from(template.render(injectDateTimeVariables(variables)));
    }

    /**
     * Injects the special variables {{current_date}}, {{current_time}}, and {{current_date_time}} into the given map.
     * @param variables the map to inject the variables into.
     * @return a copy of the map with the variables injected.
     */
    private Map<String, Object> injectDateTimeVariables(Map<String, Object> variables) {
        Map<String, Object> variablesCopy = new HashMap<>(variables);
        variablesCopy.put(CURRENT_DATE, LocalDate.now(clock));
        variablesCopy.put(CURRENT_TIME, LocalTime.now(clock));
        variablesCopy.put(CURRENT_DATE_TIME, LocalDateTime.now(clock));
        return variablesCopy;
    }

    /**
     * Create a new PromptTemplate.
     * @param template the template string of the prompt.
     * @return the PromptTemplate.
     */
    public static PromptTemplate from(String template) {
        return new PromptTemplate(template);
    }
}
