package dev.langchain4j.agentic.declarative;

import dev.langchain4j.model.chat.ChatModel;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as a supplier of the chat model to be used by an agent.
 * The method must be static and return a {@link ChatModel}.
 * <p>
 * When the method has no parameters, it is invoked once at build time to provide a fixed model.
 * When the method has parameters annotated with {@link dev.langchain4j.service.V @V},
 * they are resolved from the current {@link dev.langchain4j.agentic.scope.AgenticScope AgenticScope}
 * at each invocation, enabling dynamic model selection based on runtime state.
 * <p>
 * Example (fixed model):
 * <pre>
 * {@code
 *      public interface SupervisorBanker {
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
 * <p>
 * Example (dynamic model selection):
 * <pre>
 * {@code
 *      public interface MyEditor {
 *
 *         @Agent("Edit the story based on critique")
 *         String edit(@V("story") String story, @V("critique") CritiqueResult critique);
 *
 *         @ChatModelSupplier
 *         static ChatModel chatModel(@V("critique") CritiqueResult critique) {
 *             return critique != null && critique.score() > 8.0 ? enhancedModel() : baseModel();
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface ChatModelSupplier {
}
