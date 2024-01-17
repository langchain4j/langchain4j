package dev.langchain4j.spi.prompt;

import java.util.Map;

/**
 * A factory for creating prompt templates.
 */
public interface PromptTemplateFactory {
    /**
     * Interface for input for the factory.
     */
    interface Input {
        /**
         * Get the template string.
         * @return the template string.
         */
        String getTemplate();

        /**
         * Get the name of the template.
         * @return the name of the template.
         */
        default String getName() { return "template"; }
    }

    /**
     * Interface for a prompt template.
     */
    interface Template {
        /**
         * Render the template.
         * @param variables the variables to use.
         * @return the rendered template.
         */
        String render(Map<String, Object> variables);
    }

    /**
     * Create a new prompt template.
     * @param input the input to the factory.
     * @return the prompt template.
     */
    Template create(Input input);

}
