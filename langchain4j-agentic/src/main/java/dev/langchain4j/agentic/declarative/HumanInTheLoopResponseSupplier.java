package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a supplier of response for human-in-the-loop agent.
 * The annotated method must be static, with no arguments, and return a String response.
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
public @interface HumanInTheLoopResponseSupplier {}
