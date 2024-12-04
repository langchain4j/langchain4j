package dev.langchain4j.spi.prompt;

import dev.langchain4j.model.input.Prompt;
import java.util.Map;

/**
 * Interface for a template rendering engine.
 * <p>
 * The engine is responsible for generating a prompt by replacing variables in
 * the template with actual values provided in a map.
 */
public interface TemplateRenderingEngine {

    /**
     * Renders a template by replacing variables within the template with provided values.
     *
     * @param template The template to be rendered.
     * @param variables A map containing variable names and their corresponding values.
     * @return The rendered prompt with variables replaced by actual values.
     */
    Prompt render(Template template, Map<String, Object> variables);
}
