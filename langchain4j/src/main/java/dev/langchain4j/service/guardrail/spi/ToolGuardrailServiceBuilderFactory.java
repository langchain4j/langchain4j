package dev.langchain4j.service.guardrail.spi;

import dev.langchain4j.Experimental;
import dev.langchain4j.service.guardrail.ToolGuardrailService;
import java.util.ServiceLoader;

/**
 * A factory for providing instances of {@link ToolGuardrailService.Builder}, the tool counterpart of
 * {@link GuardrailServiceBuilderFactory}. It is looked up with {@link ServiceLoader} by
 * {@link ToolGuardrailService#builder(Class)}: the first one found replaces the default builder.
 *
 * @since 1.25.0
 */
@Experimental
public interface ToolGuardrailServiceBuilderFactory {
    /**
     * Gets an instance of the {@link ToolGuardrailService.Builder}
     *
     * @param aiServiceClass The class of the AI service whose tools are guarded
     * @return The {@link ToolGuardrailService.Builder} instance
     */
    ToolGuardrailService.Builder getBuilder(Class<?> aiServiceClass);
}
