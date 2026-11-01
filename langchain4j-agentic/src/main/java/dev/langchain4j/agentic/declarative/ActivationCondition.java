package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a static method returning a boolean as an activation condition. It has two uses.
 * <p>
 * Without a {@link #value()}, it is the condition of the agent declared by the class or interface containing the
 * method: when that agent is invoked as a subagent and the condition is false, the agent is skipped. This works for
 * any kind of agent: AI agents, workflows, planners, non-AI agents, human-in-the-loop, A2A and MCP client agents.
 * The condition is ignored when the agent is invoked directly, as the root of an agentic system. It can be evaluated
 * more than once for a single invocation, so it must be cheap and free of side effects; its arguments are read from the
 * {@code AgenticScope} without writing back the values converted to their declared types. It can also declare
 * parameters resolved through a {@link SupplierParameterResolver}.
 * <pre>
 * {@code
 *     public interface ContextGatherer {
 *
 *         @Agent(outputKey = "codeContext")
 *         String gather(@V("prOverview") String prOverview, @V("prSummary") PrSummary prSummary);
 *
 *         @ActivationCondition(description = "the triage needs context")
 *         static boolean needsContext(@V("prSummary") PrSummary prSummary) {
 *             return prSummary.needsContext();
 *         }
 *     }
 * }
 * </pre>
 * <p>
 * With a {@link #value()}, on a {@link ConditionalAgent}, it is the condition activating one or more of the
 * subagents of that conditional agent. Using a value on a class that is not a {@link ConditionalAgent} is a
 * configuration error.
 * <pre>
 * {@code
 *     public interface ExpertsAgent {
 *
 *         @ConditionalAgent(outputKey = "response",
 *                 subAgents = { MedicalExpert.class, TechnicalExpert.class, LegalExpert.class })
 *         String askExpert(@V("request") String request);
 *
 *         @ActivationCondition(value = MedicalExpert.class, description = "category is medical")
 *         static boolean activateMedical(@V("category") RequestCategory category) {
 *             return category == RequestCategory.MEDICAL;
 *         }
 *
 *         @ActivationCondition(TechnicalExpert.class)
 *         static boolean activateTechnical(@V("category") RequestCategory category) {
 *             return category == RequestCategory.TECHNICAL;
 *         }
 *
 *         @ActivationCondition(LegalExpert.class)
 *         static boolean activateLegal(AgenticScope agenticScope) {
 *             return agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.LEGAL;
 *         }
 *     }
 * }
 * </pre>
 * On a {@link ConditionalAgent}, every activation condition must have a value: a method without value is a
 * configuration error.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface ActivationCondition {

    /**
     * The subagents of the {@link ConditionalAgent} that this activation condition applies to. When empty, the
     * condition applies to the agent declared by the class or interface containing the method, which must not be a
     * {@link ConditionalAgent}.
     *
     * @return array of sub-agent classes.
     */
    Class<?>[] value() default {};

    /**
     * Description of the activation condition.
     * It should be clear and descriptive to allow understanding the purpose of the condition.
     *
     * @return description of the activation condition.
     */
    String description() default "<unknown>";
}
