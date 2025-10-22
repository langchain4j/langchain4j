package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a human-in-the-loop agent. The method can be invoked to get input or feedback from a human during the execution of a workflow.
 * The annotated method can have any number of parameters, which will be provided by the workflow context when the method is invoked.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface AudienceRetriever {
 *
 *         @HumanInTheLoop(description = "Generate a story based on the given topic", outputKey = "audience", async = true)
 *         static void request(@V("topic") String topic) {
 *             request.set("Which audience for topic " + topic + "?");
 *         }
 *
 *         @HumanInTheLoopResponseSupplier
 *         static String response() {
 *             return System.console().readLine();
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface HumanInTheLoop {

    /**
     * Name of the agent. If not provided, method name will be used.
     *
     * @return name of the agent.
     */
    String name() default "";

    /**
     * Description of the agent. This is an alias of the {@code description} attribute, and it is possible to use either.
     * It should be clear and descriptive to allow language model to understand the agent's purpose and its intended use.
     *
     * @return description of the agent.
     */
    String value() default "";

    /**
     * Description of the agent. This is an alias of the {@code value} attribute, and it is possible to use either.
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
     * If true, the agent will be invoked in an asynchronous manner, allowing the workflow to continue without waiting for the agent's result.
     *
     * @return true if the agent should be invoked in an asynchronous manner, false otherwise.
     */
    boolean async() default false;
}
