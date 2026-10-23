package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.Experimental;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as the supplier of the decision model used by a decision agent (see
 * {@link dev.langchain4j.agentic.AgenticServices#decisionAgentBuilder(Class)}). The method must be static and
 * return a {@link dev.langchain4j.model.decision.DecisionModel}.
 * <p>
 * When the method has no parameters, it is invoked once at build time to provide a fixed model.
 * When the method has parameters annotated with {@link dev.langchain4j.service.V @V}, they are resolved from the
 * current {@link dev.langchain4j.agentic.scope.AgenticScope AgenticScope} at each invocation, enabling dynamic
 * model selection based on runtime state. Parameters resolved through a {@link SupplierParameterResolver} are
 * resolved each time the method is invoked.
 * <p>
 * An interface declaring this method is recognized as a decision agent by the declarative API, also when its
 * {@link dev.langchain4j.agentic.Agent @Agent} method returns an object with several
 * {@link dev.langchain4j.service.decision.Decide @Decide} fields and is not itself annotated with {@code @Decide}.
 * It must not declare a {@link ChatModelSupplier} or a {@link StreamingChatModelSupplier}. Declaring this method on
 * any other kind of agent, like an AI agent, a workflow or a non-AI agent, fails at build time.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface CategoryRouter {
 *
 *         @Decide("Which kind of expert should handle this request?")
 *         @Agent(value = "Categorizes a user request", outputKey = "category")
 *         RequestCategory classify(@V("request") String request);
 *
 *         @DecisionModelSupplier
 *         static DecisionModel decisionModel() {
 *             return routingDecisionModel();
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
public @interface DecisionModelSupplier {}
