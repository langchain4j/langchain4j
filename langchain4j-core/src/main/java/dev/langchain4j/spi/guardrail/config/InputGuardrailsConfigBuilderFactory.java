package dev.langchain4j.spi.guardrail.config;

import dev.langchain4j.guardrail.config.InputGuardrailsConfigBuilder;
import java.util.function.Supplier;

/**
 * SPI for overriding and/or extending the default {@link InputGuardrailsConfigBuilder} implementation.
 */
public interface InputGuardrailsConfigBuilderFactory extends Supplier<InputGuardrailsConfigBuilder> {}
