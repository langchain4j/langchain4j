package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a supplier of the tool search strategy that an agent uses to discover its tools.
 * The annotated method must be static, with no arguments other than the ones resolved through a
 * {@link SupplierParameterResolver}, and return an instance of {@link ToolSearchStrategy}.
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
 *
 *         @ToolSearchStrategySupplier
 *         static ToolSearchStrategy toolSearchStrategy() {
 *             return new SimpleToolSearchStrategy();
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface ToolSearchStrategySupplier {}
