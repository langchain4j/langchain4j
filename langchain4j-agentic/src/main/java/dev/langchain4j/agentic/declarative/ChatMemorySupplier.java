package dev.langchain4j.agentic.declarative;

import dev.langchain4j.memory.ChatMemory;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as a supplier of chat memory that an agent can utilize during its operation.
 * The annotated method must be static, with no arguments, and return an instance of {@link ChatMemory}.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface SupportAgent {
 *         @Agent("A customer support agent")
 *         String assist(@V("customerQuery") String customerQuery);
 *
 *         @ChatMemorySupplier
 *         static ChatMemory chatMemory() {
 *             return MessageWindowChatMemory.withMaxMessages(10);
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface ChatMemorySupplier {
}
