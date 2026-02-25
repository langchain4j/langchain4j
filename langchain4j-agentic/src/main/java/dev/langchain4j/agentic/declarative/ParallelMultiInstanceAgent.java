package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.agentic.Agent;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a definition of a parallel multi-instance agent, used to orchestrate the agentic workflow
 * by creating multiple instances of the same sub-agent, one for each item in a collection read from the agentic scope.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface BatchProcessorAgent {
 *
 *         @ParallelMultiInstanceAgent( outputKey = "results",
 *                 subAgent = ItemProcessor.class,
 *                 inputKey = "items" )
 *         List<Result> process(@V("items") List<Item> items);
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface ParallelMultiInstanceAgent {

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
    Class<? extends TypedKey<?>> typedOutputKey() default Agent.NoTypedKey.class;

    /**
     * The sub-agent class that will be instantiated for each item in the collection.
     *
     * @return the sub-agent class.
     */
    Class<?> subAgent();

    /**
     * Key referencing a collection in the agentic scope. For each item in this collection,
     * an instance of the sub-agent will be created and executed in parallel.
     *
     * @return the key of the input collection.
     */
    String inputCollection();
}
