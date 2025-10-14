package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a supervisor agent that can autonomously coordinate and invoke multiple sub-agents.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface SupervisorBanker {
 *
 *         @SupervisorAgent(responseStrategy = SupervisorResponseStrategy.SUMMARY, subAgents = {
 *                 @SubAgent(type = WithdrawAgent.class),
 *                 @SubAgent(type = CreditAgent.class)
 *         })
 *         String invoke(@V("request") String request);
 *
 *         @ChatModelSupplier
 *         static ChatModel chatModel() {
 *             return plannerModel();
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface SupervisorAgent {

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
     * Array of sub-agents that can be invoked by the supervisor agent.
     *
     * @return array of sub-agents.
     */
    SubAgent[] subAgents();

    /**
     * Maximum number of sub-agent invocations allowed during a single supervisor agent execution.
     * This limit helps prevent infinite loops and excessive resource consumption.
     *
     * @return maximum number of sub-agent invocations.
     */
    int maxAgentsInvocations() default 10;

    /**
     * Strategy for providing context to the supervisor agent.
     */
    SupervisorContextStrategy contextStrategy() default SupervisorContextStrategy.CHAT_MEMORY;

    /**
     * Strategy to decide which response the supervisor agent should return.
     */
    SupervisorResponseStrategy responseStrategy() default SupervisorResponseStrategy.LAST;
}
