package dev.langchain4j.spi.guardrail;

import dev.langchain4j.service.guardrail.GuardrailService;

/**
 * Factory interface for creating instances of {@link GuardrailService}. This factory
 * is designed to produce dynamically typed instances of a service that manages guardrails,
 * constraints, or validations applied to methods within AI services.
 */
public interface GuardrailServiceFactory {
    /**
     * Creates an instance of a {@link GuardrailService} based on the provided AI service class.
     *
     * @param <MK> The type representing a method key, which can be used to identify and manage
     *             methods of the AI service class for guardrail application purposes.
     * @param <T> The type of the {@link GuardrailService} to be created.
     * @param aiServiceClass The class representing the AI service for which the {@link GuardrailService} is being constructed.
     *                       This class serves as the basis for applying guardrails, constraints, or validations.
     * @return A new instance of type {@link GuardrailService} associated with the specified AI service class.
     *         This instance enables the execution of guardrails configured for the specified service.
     */
    <MK, T extends GuardrailService<MK>> T create(Class<?> aiServiceClass);
}
