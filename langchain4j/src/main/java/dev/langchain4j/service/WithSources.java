package dev.langchain4j.service;

import dev.langchain4j.rag.content.Content;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Represents a container holding augmented information associated with a response.
 *
 * @param <T> The type of the response.
 */
@Getter
@Builder
public class WithSources<T> {
    private T response; // The response associated with the augmented information.
    private List<Content> retrievedContents; // Wrapper for the augmentation details.
}
