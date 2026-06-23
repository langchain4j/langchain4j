package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a static method as a supplier of the {@link dev.langchain4j.agentic.planner.AgentRegistry AgentRegistry}
 * to be used for dynamic agent discovery.
 * The method must be static, have no parameters, and return an {@link dev.langchain4j.agentic.planner.AgentRegistry AgentRegistry}.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface SupervisorBanker {
 *
 *         @SupervisorAgent(subAgents = { WithdrawAgent.class, CreditAgent.class })
 *         String invoke(@V("request") String request);
 *
 *         @AgentRegistrySupplier
 *         static AgentRegistry registry() {
 *             InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
 *             registry.register(exchangeAgent);
 *             return registry;
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface AgentRegistrySupplier {}
