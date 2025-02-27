package dev.langchain4j.guardrail.config;

/**
 * Builder for {@link InputGuardrailsConfig} instances.
 * <p>
 *     This is needed so other frameworks (like Quarkus and Spring) can extend the configuration mechanism with their own
 *     implementations while also adhering to the interfaces and specs defined here.
 * </p>
 */
public interface InputGuardrailsConfigBuilder extends GuardrailsConfigBuilder<InputGuardrailsConfig> {}
