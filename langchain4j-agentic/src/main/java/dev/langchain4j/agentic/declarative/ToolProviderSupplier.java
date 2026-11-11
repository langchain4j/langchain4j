package dev.langchain4j.agentic.declarative;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.service.tool.ToolProvider;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;


import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as a supplier of a tool provider that an agent can utilize during its operation.
 * The annotated method must be static on the agent type, with no arguments other than the ones resolved through a
 * {@link SupplierParameterResolver}, and return an instance of {@link ToolProvider}.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface BankAgent {
 *         @Agent("A banker agent")
 *         String credit(@V("user") String user, @V("amountInUSD") Double amount);
 *
 *         @ToolProviderSupplier
 *         static ToolProvider toolProvider() {
 *             return ToolProviders.from(bankTool, currencyConverterTool);
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
public @interface ToolProviderSupplier {
}
