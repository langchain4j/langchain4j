package dev.langchain4j.service.guardrail;

import dev.langchain4j.Experimental;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares {@link ToolOutputGuardrail}s for tools.
 * <p>
 * On a {@code @Tool} method the guardrails apply to that tool; on the class declaring the tools they apply
 * to every tool in that class. Guardrails run class-level first, then method-level. Tools without a
 * declaring class - MCP tools, or tools registered programmatically - are guarded through
 * {@code AiServices.toolOutputGuardrails(...)} instead, which applies to every tool of the service.
 * <p>
 * Guardrail classes are instantiated through {@link dev.langchain4j.classinstance.ClassInstanceLoader},
 * so frameworks that supply their own instances - CDI in Quarkus, the context in Spring - get their
 * managed beans here.
 * <p>
 * Guardrail classes declared here must be {@code public} and have a no-argument constructor, since they
 * are instantiated reflectively.
 *
 * @since 1.19.0
 */
@Experimental
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ToolOutputGuardrails {

    Class<? extends ToolOutputGuardrail>[] value();
}
