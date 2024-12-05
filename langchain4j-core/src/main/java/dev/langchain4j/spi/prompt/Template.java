package dev.langchain4j.spi.prompt;

import java.util.Map;

/**
 * Interface for a prompt template.
 */
public interface Template {

    /**
     * Retrieves the content of the template.
     * <p>
     * It usually contains placeholders like <code>{{variable}}`</code>.
     *
     * @return the content of the template as a String.
     */
    String content();

    /**
     * Render the template.
     *
     * @param variables the variables to use.
     * @return the rendered template.
     */
    String render(Map<String, Object> variables);
}
