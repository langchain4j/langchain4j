package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a pre-invocation hook for an agentic pattern.
 * The annotated method must be static on the agent type, accept an {@link AgenticScope} as argument, and return void.
 * It can also declare parameters resolved through a {@link SupplierParameterResolver}.
 * It will be invoked before every execution of the agent, allowing initialization or transformation
 * of the shared state.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface StoryCreatorWithDefaults {
 *
 *         @SequenceAgent(outputKey = "story",
 *                 subAgents = { CreativeWriter.class, StyleEditor.class })
 *         String write(@V("topic") String topic);
 *
 *         @BeforeCall
 *         static void beforeCall(AgenticScope agenticScope) {
 *             agenticScope.writeStateIfAbsent("style", "comedy");
 *         }
 *     }
 * }
 * </pre>
 * <p>
 * Like every helper method, it can be an instance method of a helper object of the agent instead of a static method
 * of the agent type: see the "Helper objects" section of the agentic tutorial and
 * {@link AgenticServices.AgentConfigurator}.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface BeforeCall {}
