package dev.langchain4j.spi.prompt;

import dev.langchain4j.model.input.PromptTemplate;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Interface for a source of prompt templates.
 * <p>
 * A source can provide prompt templates
 * identified by a unique template ID. Implementations of this interface are responsible
 * for retrieving and returning {@link PromptTemplate} instances based on the provided ID
 * and optional tags.
 */
public interface PromptTemplateSource {

    /**
     * Retrieves a {@link PromptTemplate} based on the provided template ID with default tags.
     *
     * @param promptTemplateId The unique identifier of the prompt template to retrieve.
     * @return The corresponding {@link PromptTemplate} for the provided template ID.
     */
    default @Nullable PromptTemplate getPromptTemplate(@NonNull String promptTemplateId) {
        throw new UnsupportedOperationException("PromptTemplateSource.getPromptTemplate(String) is not implemented");
    }
}
