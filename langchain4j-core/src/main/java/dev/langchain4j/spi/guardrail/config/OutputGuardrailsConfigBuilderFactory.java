package dev.langchain4j.spi.guardrail.config;

import dev.langchain4j.guardrail.config.OutputGuardrailsConfigBuilder;
import java.util.function.Supplier;

/**
 * SPI for overriding and/or extending the default {@link OutputGuardrailsConfigBuilder} implementation.
 */
public interface OutputGuardrailsConfigBuilderFactory extends Supplier<OutputGuardrailsConfigBuilder> {}
