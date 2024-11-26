package dev.langchain4j.spi.prompt;

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
     * Create a new prompt template.
     * @param input the input to the factory.
     * @return the prompt template.
     */
    Template create(Input input);

}
