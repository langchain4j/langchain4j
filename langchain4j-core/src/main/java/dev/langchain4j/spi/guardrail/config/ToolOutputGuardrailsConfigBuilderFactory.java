package dev.langchain4j.spi.guardrail.config;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import java.util.function.Supplier;

/**
 * SPI for overriding and/or extending the default {@link ToolOutputGuardrailsConfig.ToolOutputGuardrailsConfigBuilder} implementation.
 *
 * @since 1.24.0
 */
@Experimental
public interface ToolOutputGuardrailsConfigBuilderFactory
        extends Supplier<ToolOutputGuardrailsConfig.ToolOutputGuardrailsConfigBuilder> {}
