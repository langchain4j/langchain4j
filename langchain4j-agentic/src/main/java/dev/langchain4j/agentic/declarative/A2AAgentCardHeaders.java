package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.agentic.AgenticServices;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a static method returning the {@code Map<String, String>} of request headers used to fetch an A2A agent card.
 * The method runs lazily, when the card is first needed, and again when a failed fetch is retried. A successful
 * card is cached, so the method is not invoked for every agent invocation. Returning {@code null} or an empty map
 * adds no headers. These headers authenticate card discovery only, not A2A messages.
 * <p>
 * The method can only declare parameters resolved through a {@link SupplierParameterResolver}. Those parameters
 * are resolved once, when the agent is built, while the method itself normally runs on a LangChain4j executor
 * thread within the card resolution timeout. Pass a token provider as a resolved parameter, rather than a token
 * captured at build time, when credentials need refreshing between fetch attempts.
 * <p>
 * Like other helper methods, it can be an instance method on a helper object instead of a static method on the
 * agent type: see the "Helper objects" section of the agentic tutorial and
 * {@link AgenticServices.AgentConfigurator}. Exactly one helper can supply the card headers of an agent.
 *
 * @since 1.24.0
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface A2AAgentCardHeaders {}
