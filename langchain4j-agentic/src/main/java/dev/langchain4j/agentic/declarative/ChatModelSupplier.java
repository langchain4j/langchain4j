package dev.langchain4j.agentic.declarative;

import dev.langchain4j.model.chat.ChatModel;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as a supplier of the chat model(s) to be used by an agent.
 * The method must be static and return either a single {@link ChatModel} or a {@code ChatModel[]}.
 * <p>
 * When returning a {@code ChatModel[]}, a {@link ChatModelSelectorSupplier} method must also be
 * present on the same agent interface to select which model to use at each invocation.
 * <p>
 * Example (single model):
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
 * Example (multiple models with selector):
 * <pre>
 * {@code
 *      public interface MyEditor {
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
public @interface ChatModelSupplier {
}
