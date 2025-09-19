package dev.langchain4j.model.input;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.singletonMap;

import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.spi.prompt.PromptTemplateFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a template of a prompt that can be reused multiple times.
 * A template typically contains one or more variables (placeholders) defined as {{variable_name}} that are
 * replaced with actual values to produce a Prompt.
 * Special variables {{current_date}}, {{current_time}}, and {{current_date_time}} are automatically
 * filled with LocalDate.now(), LocalTime.now(), and LocalDateTime.now() respectively.
 */
public class PromptTemplate {

    private static final PromptTemplateFactory FACTORY = factory();

    private static PromptTemplateFactory factory() {
        for (PromptTemplateFactory factory : loadFactories(PromptTemplateFactory.class)) {
            return factory;
        }
        return new DefaultPromptTemplateFactory();
    }

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
     *
     * @param template the template string of the prompt.
     * @param clock    the clock to use for the special variables.
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
     *
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
     *
     * @param template the template string of the prompt.
     * @return the PromptTemplate.
     */
    public static PromptTemplate from(String template) {
        return new PromptTemplate(template);
    }

    /**
     * Creates a {@link PromptTemplate} by reading the content from the provided {@link DocumentSource}
     * using the default character set {@link StandardCharsets#UTF_8}.
     * <p>
     * This method reads the template content from the {@link InputStream} returned by
     * {@link DocumentSource#inputStream()}, then closes the stream after reading.
     * </p>
     * <h3>When to use</h3>
     * Use this method when your prompt templates are stored outside of your Java source code
     * and need to be dynamically loaded at runtime. For example:
     * <ul>
     *   <li>Templates stored in <b>classpath resources</b> (e.g., {@code src/main/resources})</li>
     *   <li>Templates fetched from a <b>remote URL</b> (HTTP, FTP, etc.)</li>
     *   <li>Templates managed in <b>configuration management systems</b> (e.g., Consul, AWS SSM, Vault)</li>
     *   <li>Templates stored on <b>file systems</b> that need to be read via an {@link InputStream}</li>
     * </ul>
     * Using {@link DocumentSource} allows you to support all these scenarios with a single method signature.
     *
     * @param source the {@link DocumentSource} to read the template from; must not be {@code null}.
     * @return a {@link PromptTemplate} instance containing the content read from the {@code source}.
     * @throws RuntimeException if an I/O error occurs while reading from the source.
     * @throws NullPointerException if {@code source} is {@code null}.
     */
    public static PromptTemplate fromDocumentSource(DocumentSource source) {
        return fromDocumentSource(source, StandardCharsets.UTF_8);
    }

    /**
     * Creates a {@link PromptTemplate} by reading the content from the provided {@link DocumentSource}
     * using the given {@link Charset}.
     * <p>
     * This method reads the template content from the {@link InputStream} returned by
     * {@link DocumentSource#inputStream()} and closes the stream after reading.
     * If the provided {@code charset} is {@code null}, {@link StandardCharsets#UTF_8} is used as the default.
     * </p>
     * <h3>When to use</h3>
     * Use this method when:
     * <ul>
     *   <li>You need to load templates from different sources like resources, file paths, URLs, or config stores</li>
     *   <li>The template content might be encoded in a <b>non-UTF-8 charset</b> and you need to specify it</li>
     *   <li>You want a consistent way to handle stream closing and error handling</li>
     * </ul>
     * This approach keeps the template loading logic decoupled from the storage or transport mechanism,
     * making it easy to swap sources without changing calling code.
     *
     * @param source  the {@link DocumentSource} to read the template from; must not be {@code null}.
     * @param charset the {@link Charset} to use for decoding the template content; if {@code null}, UTF-8 will be used.
     * @return a {@link PromptTemplate} instance containing the content read from the {@code source}.
     * @throws RuntimeException if an I/O error occurs while reading from the source.
     * @throws NullPointerException if {@code source} is {@code null}.
     */
    public static PromptTemplate fromDocumentSource(DocumentSource source, Charset charset) {
        ensureNotNull(source, "source");
        String template;
        try {
            template = Utils.readInputStreamAndClose(source.inputStream(), charset);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return from(template);
    }
}
