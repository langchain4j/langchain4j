package dev.langchain4j.service.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import dev.langchain4j.service.guardrail.spi.ToolGuardrailServiceBuilderFactory;
import dev.langchain4j.service.tool.AiServiceTool;
import java.util.ServiceLoader;

/**
 * Executes the tool guardrails of the tools of an AI service: the {@link ToolInputGuardrail}s, which validate or
 * rewrite the arguments of a tool call before the tool runs, and the {@link ToolOutputGuardrail}s, which validate or
 * rewrite its result before the LLM sees it. It is the tool counterpart of {@link GuardrailService}, and its
 * {@link #targetClass()} is the AI service class too.
 * <p>
 *     <strong>Keys.</strong> The key of a tool is the {@link ToolKey} that {@link #bind(Class, AiServiceTool)}
 *     returns for it: it is only meaningful to the service that created it. A tool provided anew for every
 *     invocation (by a {@link dev.langchain4j.service.tool.ToolProvider}, for example an MCP server) is bound for every
 *     invocation.
 * </p>
 * <p>
 *     <strong>Which guardrails run.</strong> Every tool guardrail that applies to a tool runs, as one chain, in this
 *     order, separately for input and output:
 * </p>
 * <ol>
 *     <li>the guardrails given to the builder (set on the {@code AiServices} builder; they apply to every tool)</li>
 *     <li>the guardrails of the {@link ToolInputGuardrails} / {@link ToolOutputGuardrails} annotation of the class of
 *     the tool</li>
 *     <li>the guardrails of the annotation of the tool method, then the guardrails of the {@link AiServiceTool}</li>
 * </ol>
 * <p>
 *     No level replaces another. This deliberately differs from the AI service guardrails of {@link GuardrailService},
 *     where the builder replaces the annotations. A guardrail that applies to every tool can skip some tools by looking
 *     at {@link ToolInputGuardrailRequest#toolName()} or {@link ToolInputGuardrailRequest#toolSource()}.
 * </p>
 * <p>
 *     A guardrail class that is given at several levels (by annotations or {@code *GuardrailClasses(..)}) runs once,
 *     at its first position. Guardrail instances only count as duplicates of themselves (the same object twice), since
 *     two instances of one class can be configured differently, and an instance and a class naming the same class
 *     both run.
 * </p>
 * <p>
 *     <strong>Results.</strong> A non-fatal failure is returned as a failed result, never thrown, so that the caller
 *     can send it to the LLM as an error tool result. A fatal failure throws a
 *     {@link dev.langchain4j.guardrail.ToolInputGuardrailException} or a
 *     {@link dev.langchain4j.guardrail.ToolOutputGuardrailException} (the asynchronous methods fail their future
 *     with it).
 * </p>
 * <p>
 *     <strong>Customizing.</strong> The default implementation finds the annotations through
 *     {@link dev.langchain4j.spi.classloading.ClassMetadataProviderFactory} and creates guardrails from their classes
 *     through {@link dev.langchain4j.spi.classloading.ClassInstanceFactory}, like the AI service guardrails. A
 *     framework can also replace the whole builder with a {@link ToolGuardrailServiceBuilderFactory}.
 * </p>
 *
 * @since 1.24.0
 */
@Experimental
public non-sealed interface ToolGuardrailService
        extends GenericGuardrailService<
                ToolInputGuardrailRequest,
                ToolInputGuardrailResult,
                ToolOutputGuardrailRequest,
                ToolOutputGuardrailResult> {

    /**
     * Binds a tool to this service: resolves the tool guardrails that apply to the tool, and returns the key to run
     * them with.
     *
     * @param toolClass The class whose {@link ToolInputGuardrails} / {@link ToolOutputGuardrails} annotations apply to
     *                  the tool: the class of the object the {@code @Tool} method of the tool was found on (the object
     *                  given to {@code AiServices.tools(..)}). {@code null} for a tool that was not found on an object
     *                  (an {@link AiServiceTool} given directly, or provided by a
     *                  {@link dev.langchain4j.service.tool.ToolProvider} such as an MCP server); the declaring class of
     *                  the tool method is then used, if the tool has one.
     * @param tool      The tool. Must not be null.
     * @return The {@link ToolKey} that identifies this tool to this service: pass it to
     *         {@link #executeInputGuardrails(Object, dev.langchain4j.guardrail.GuardrailRequest)},
     *         {@link #executeOutputGuardrails(Object, dev.langchain4j.guardrail.GuardrailRequest)}, their asynchronous
     *         variants, and {@link #hasInputGuardrails(Object)} / {@link #hasOutputGuardrails(Object)}. It is only
     *         meaningful to the service that returned it, and never {@code null}. A tool without guardrails also
     *         gets a key, and running its guardrails returns a success.
     */
    ToolKey bind(Class<?> toolClass, AiServiceTool tool);

    /**
     * Creates a builder of {@link ToolGuardrailService}. If a {@link ToolGuardrailServiceBuilderFactory} is found by
     * {@link ServiceLoader}, the first one found provides the builder; otherwise the default builder is used.
     *
     * @param aiServiceClass The class of the AI service whose tools are guarded. Must not be null.
     * @return The builder.
     */
    static Builder builder(Class<?> aiServiceClass) {
        return ServiceLoader.load(ToolGuardrailServiceBuilderFactory.class)
                .findFirst()
                .map(builderFactory -> builderFactory.getBuilder(aiServiceClass))
                .orElseGet(() -> new ToolGuardrailServiceBuilder(aiServiceClass));
    }

    /**
     * Identifies a tool to the {@link ToolGuardrailService} that bound it (see {@link #bind(Class, AiServiceTool)}),
     * as the key of the {@code execute*} and {@code has*} methods. It is only meaningful to that service: what else it
     * holds is up to the implementation of the service.
     *
     * @since 1.24.0
     */
    @Experimental
    interface ToolKey {
        /**
         * The name of the tool, as the LLM sees it.
         *
         * @return The name of the tool.
         */
        String toolName();
    }

    /**
     * Builds a {@link ToolGuardrailService}. The guardrails and the guardrail classes it is given apply to every tool
     * and run first (see {@link ToolGuardrailService}); the configurations apply to the executors of every tool.
     *
     * @since 1.24.0
     */
    @Experimental
    non-sealed interface Builder
            extends GenericGuardrailService.Builder<
                    ToolInputGuardrailRequest,
                    ToolInputGuardrailResult,
                    ToolOutputGuardrailRequest,
                    ToolOutputGuardrailResult,
                    ToolGuardrailService,
                    ToolInputGuardrail,
                    ToolOutputGuardrail,
                    ToolInputGuardrailsConfig,
                    ToolOutputGuardrailsConfig,
                    Builder> {}
}
