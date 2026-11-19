package dev.langchain4j.service.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.AiServiceTool;

/**
 * Applies {@link ToolOutputGuardrail}s to a tool: they validate, or rewrite, the result of the tool
 * <strong>before</strong> the LLM sees it. This is the place to protect against indirect prompt injection in
 * text returned by tools.
 * <p>
 *     The annotation can be placed on a tool method (annotated with {@link Tool @Tool})
 *     or on the class declaring tool methods. On a class, it applies to every tool method of that class.
 * </p>
 * <p>
 *     A class annotation is looked up on the class of the tool, then on its superclasses, then on its interfaces, and
 *     the first one found is used: an annotation on a subclass hides the one of its parents, it does not add to it.
 * </p>
 * <p>
 *     Every tool guardrail that applies to a tool runs, as one chain, in this order:
 * </p>
 * <ol>
 *     <li>the guardrails set on the {@link AiServices} builder (they apply to every tool)</li>
 *     <li>the guardrails of the class annotation</li>
 *     <li>the guardrails of the method annotation</li>
 *     <li>the guardrails of the {@link AiServiceTool} of the tool</li>
 * </ol>
 * <p>
 *     No level replaces another. This <strong>differs</strong>
 *     from {@link OutputGuardrails}, where the guardrails set on the builder replace the ones of the annotations.
 *     A guardrail that must not apply to some tools can skip them by checking
 *     {@link ToolOutputGuardrailRequest#toolName() toolName()} or
 *     {@link ToolOutputGuardrailRequest#toolSource() toolSource()}.
 * </p>
 * <p>
 *     The guardrails also run on error results; use
 *     {@link ToolOutputGuardrailRequest#isError() isError()} to skip them. Unlike
 *     {@link OutputGuardrails}, there is no retry or reprompt: a failure is sent to the LLM as the result of the tool.
 * </p>
 * <p>
 *     Duplicates are removed, keeping the first occurrence: the same guardrail <strong>class</strong> declared by
 *     annotations or by the {@code toolOutputGuardrailClasses(..)} methods of the builder, at any level, runs once for
 *     a tool. Guardrail <strong>instances</strong> set on the builder or on an
 *     {@link AiServiceTool} are only duplicates of themselves (the same
 *     object twice), since two instances of one class can differ in their state, and an instance and a class naming
 *     the same class both run.
 * </p>
 * <p>
 *     This annotation is unrelated to {@link OutputGuardrails}, which applies to the response of an AI service.
 *     On a class that is both an AI service and is used as a tool, each annotation keeps its own meaning.
 * </p>
 *
 * @since 1.25.0
 */
@Experimental
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ToolOutputGuardrails {

    /**
     * The tool output guardrail classes to apply. They are instantiated through the same mechanism as the classes of
     * {@link OutputGuardrails}.
     *
     * @return the guardrail classes, in the order they are applied.
     */
    Class<? extends ToolOutputGuardrail>[] value();
}
