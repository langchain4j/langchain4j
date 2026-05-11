package dev.langchain4j.agentic.declarative;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as a chat model selector for an agent that has multiple chat models
 * provided via {@link ChatModelSupplier} returning a {@code ChatModel[]}. The annotated
 * method must be static, take parameters annotated with {@link dev.langchain4j.service.V @V}
 * (resolved from the current {@link dev.langchain4j.agentic.scope.AgenticScope AgenticScope}),
 * and return an {@code int} representing the index of the model to use.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface MyEditor {
 *
 *         @Agent("Edit the story based on critique")
 *         String edit(@V("story") String story, @V("critique") CritiqueResult critique);
 *
 *         @ChatModelSupplier
 *         static ChatModel[] chatModels() {
 *             return new ChatModel[] { baseModel(), enhancedModel() };
 *         }
 *
 *         @ChatModelSelectorSupplier
 *         static int selectModel(@V("critique") CritiqueResult critique) {
 *             return critique != null && critique.score() > 8.0 ? 1 : 0;
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface ChatModelSelectorSupplier {
}
