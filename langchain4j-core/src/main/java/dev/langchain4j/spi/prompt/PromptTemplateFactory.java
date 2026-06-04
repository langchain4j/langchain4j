package dev.langchain4j.spi.prompt;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import java.util.List;
import java.util.Map;

/**
 * A factory for creating prompt templates.
 */
@Internal
public interface PromptTemplateFactory {

    /**
     * Interface for input for the factory.
     */
    @Internal
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
        default String getName() {
            return "template";
        }
    }

    /**
     * Interface for a prompt template.
     */
    @Internal
    interface Template {
        /**
         * Render the template.
         * @param variables the variables to use.
         * @return the rendered template.
         */
        String render(Map<String, Object> variables);

        /**
         * Renders placeholders into multimodal-ready {@linkplain Content content segments}.
         * Plain values are turned into merged {@linkplain TextContent} runs to mirror {@link #render(Map)} semantics.
         * Variables that resolve to {@link Content} or ordered lists whose elements are exclusively {@link Content}
         * instances are inserted without string conversion.
         * <p>
         * Implementations supplied by integrations that only support textual rendering rely on this default implementation.
         *
         * @param variables Resolved template variables (typically including {@code current_date}, etc.).
         */
        default List<Content> renderContents(Map<String, Object> variables) {
            String rendered = render(variables);
            return List.of(TextContent.from(rendered));
        }
    }

    /**
     * Create a new prompt template.
     * @param input the input to the factory.
     * @return the prompt template.
     */
    Template create(Input input);
}
