package dev.langchain4j.agentic.declarative;

import dev.langchain4j.agentic.Agent;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a definition of a conditional agent, generally used to route the agentic workflow toward
 * one or more sub-agents according to the verification of their activation conditions.
 * Each sub-agent has its own activation condition, a static method annotated with {@link ActivationCondition} that
 * determines when it should be invoked.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface ExpertsAgent {
 *
 *         @ConditionalAgent(outputKey = "response",
 *                           subAgents = { MedicalExpert.class, TechnicalExpert.class, LegalExpert.class } )
 *         String askExpert(@V("request") String request);
 *
 *         @ActivationCondition(MedicalExpert.class)
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
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface ConditionalAgent {

    /**
     * Name of the agent. If not provided, method name will be used.
     *
     * @return name of the agent.
     */
    String name() default "";

    /**
     * Description of the agent.
     * It should be clear and descriptive to allow language model to understand the agent's purpose and its intended use.
     *
     * @return description of the agent.
     */
    String description() default "";

    /**
     * Key of the output variable that will be used to store the result of the agent's invocation.
     *
     * @return name of the output variable.
     */
    String outputKey() default "";

    /**
     * Strongly typed key of the output variable that will be used to store the result of the agent's invocation.
     * It enforces type safety when retrieving the output from the agent's state and can be used in alternative
     * to the {@code outputKey()} attribute. Note that only one of those two attributes can be used at a time.
     *
     * @return class representing the typed output variable.
     */
    Class<? extends AgentState<?>> typedOutputKey() default Agent.NoAgentState.class;

    /**
     * Sub-agents that can be conditionally activated by this agent.
     *
     * @return array of sub-agents.
     */
    Class<?>[] subAgents();
}
