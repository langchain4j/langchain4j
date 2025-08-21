package dev.langchain4j.agentic.declarative;

import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

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

    String outputName() default "";

    SubAgent[] subAgents();

    int maxAgentsInvocations() default 10;

    SupervisorContextStrategy contextStrategy() default SupervisorContextStrategy.CHAT_MEMORY;

    SupervisorResponseStrategy responseStrategy() default SupervisorResponseStrategy.LAST;
}
