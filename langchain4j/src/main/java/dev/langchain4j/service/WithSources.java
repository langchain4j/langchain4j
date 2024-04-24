package dev.langchain4j.service;

import dev.langchain4j.data.message.AugmentedMessage;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents a container holding augmented information associated with a response.
 *
 * @param <T> The type of the response.
 */
@Getter
@Builder
public class WithSources<T> {
    private T response; // The response associated with the augmented information.
    private AugmentedMessage augmentedMessage; // Wrapper for the augmentation details.
}
