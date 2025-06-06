package dev.langchain4j.spi.guardrail.config;

import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import java.util.function.Supplier;

/**
 * SPI for overriding and/or extending the default {@link InputGuardrailsConfig.InputGuardrailsConfigBuilder} implementation.
 */
public interface InputGuardrailsConfigBuilderFactory
        extends Supplier<InputGuardrailsConfig.InputGuardrailsConfigBuilder> {}
