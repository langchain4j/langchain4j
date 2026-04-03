package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as an activation predicate for one or more sub-agents of a conditional agent.
 * The method must be static and return a boolean indicating whether the sub-agent(s) should be activated.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface ExpertsAgent {
 *
 *         @ConditionalAgent(outputKey = "response", subAgents = {
 *                 @SubAgent(type = MedicalExpert.class, outputKey = "response"),
 *                 @SubAgent(type = TechnicalExpert.class, outputKey = "response"),
 *                 @SubAgent(type = LegalExpert.class, outputKey = "response")
 *         })
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
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface ActivationCondition {

    /**
     * One or more sub-agent classes that this activation condition applies to.
     *
     * @return array of sub-agent classes.
     */
    Class<?>[] value();

    /**
     * Description of the activation condition.
     * It should be clear and descriptive to allow understanding the purpose of the condition.
     *
     * @return description of the activation condition.
     */
    String description() default "<unknown>";
}
