package dev.langchain4j.agentic.declarative;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as an activation condition for one or more sub-agents of a conditional agent.
 * The method must be static and return a boolean indicating whether the sub-agent(s) should be activated.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface ExpertsAgent {
 *
 *         @ConditionalAgent(outputName = "response", subAgents = {
 *                 @SubAgent(type = MedicalExpert.class, outputName = "response"),
 *                 @SubAgent(type = TechnicalExpert.class, outputName = "response"),
 *                 @SubAgent(type = LegalExpert.class, outputName = "response")
 *         })
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
public @interface ActivationCondition {
    Class<?>[] value();
}
