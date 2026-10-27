package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.Experimental;
import dev.langchain4j.service.decision.ThresholdContext;
import dev.langchain4j.service.decision.ThresholdProvider;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as the supplier of the threshold of the {@code boolean} answers of a decision agent: the answer is
 * {@code true} when the probability of "yes" is at least the threshold, 0.5 when no supplier is declared. The method
 * must be static, take the {@link ThresholdContext} describing the question, and
 * return a {@code double} between 0 and 1, or a {@code Double} that is {@code null} to use the default of 0.5. Like a
 * {@link ThresholdProvider}, it is invoked at every invocation of the agent, so the
 * threshold can come from configuration that changes at runtime, and it can be called concurrently: it must be
 * thread-safe, fast and non-blocking. Its other parameters, if any, must be resolved by a
 * {@link SupplierParameterResolver}, which is called at every invocation too. Declaring this method on any other kind
 * of agent, like an AI agent, a workflow or a non-AI agent, fails at build time.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface TransactionGate {
 *
 *         @Decide("Does this transaction look fraudulent?")
 *         @Agent(value = "Checks a transaction for fraud", outputKey = "suspicious")
 *         boolean check(@V("transaction") String transaction);
 *
 *         @DecisionModelSupplier
 *         static DecisionModel decisionModel() {
 *             return fraudDecisionModel();
 *         }
 *
 *         // a transaction is suspicious when the probability of "yes" reaches the threshold tuned for the model
 *         // that answered, read from a system property at every invocation so that it can be changed at runtime
 *         @DecisionThresholdSupplier
 *         static double threshold(ThresholdContext context) {
 *             return Double.parseDouble(System.getProperty("fraud.threshold." + context.modelName(), "0.5"));
 *         }
 *     }
 * }
 * </pre>
 *
 * @since 1.23.0
 */
@Experimental
@Retention(RUNTIME)
@Target({METHOD})
public @interface DecisionThresholdSupplier {}
