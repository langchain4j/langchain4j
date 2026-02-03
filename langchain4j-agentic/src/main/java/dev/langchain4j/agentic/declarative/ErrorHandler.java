package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as an error handler for a workflow agent.
 * The annotated method must be static, an {@link ErrorContext} as argument and return an instance of {@link ErrorRecoveryResult}.
 * It will be invoked when an error occurs during the agent's operation, allowing for custom error handling logic.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface StoryCreatorWithErrorRecovery {
 *
 *         @SequenceAgent(outputKey = "story", subAgents = {
 *                 @SubAgent(type = CreativeWriter.class, outputKey = "story"),
 *                 @SubAgent(type = AudienceEditor.class, outputKey = "story"),
 *                 @SubAgent(type = StyleEditor.class, outputKey = "story")
 *         })
 *         String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
 *
 *         @ErrorHandler
 *         static ErrorRecoveryResult errorHandler(ErrorContext errorContext) {
 *             if (errorContext.agentName().equals("generateStory") &&
 *                     errorContext.exception() instanceof MissingArgumentException mEx && mEx.argumentName().equals("topic")) {
 *                 errorContext.agenticScope().writeState("topic", "dragons and wizards");
 *                 return ErrorRecoveryResult.retry();
 *             }
 *             return ErrorRecoveryResult.throwException();
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface ErrorHandler {}
