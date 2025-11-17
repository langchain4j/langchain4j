package dev.langchain4j.agentic.declarative;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as a supplier of tools that an agent can utilize during its operation.
 * The annotated method must be static, with no arguments, and return a single Object or an array of Objects.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface BankAgent {
 *         @Agent("A banker agent")
 *         String credit(@V("user") String user, @V("amountInUSD") Double amount);
 *
 *         @ToolsSupplier
 *         static Object[] tools() {
 *             return new Object[] { bankTool, currencyConverterTool };
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface ToolsSupplier {
}
