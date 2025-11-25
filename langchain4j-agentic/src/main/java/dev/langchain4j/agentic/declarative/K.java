package dev.langchain4j.agentic.declarative;

import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Function;

/**
 * A parameter of an agentic method is annotated with {@code @K} to indicate that it is populated with
 * the value of a key representing a specific typed state in the agentic system.
 * That parameter also becomes a prompt template variable, so its value will be injected into prompt templates defined
 * via @{@link UserMessage}, @{@link SystemMessage} and {@link AiServices#systemMessageProvider(Function)}.
 * The variable name to be used in the prompt template corresponds to the one returned by the {@link AgentState#name()}
 * method of the class specified as the value of this annotation, which by default is the simple name of the class
 * implementing the {@link AgentState} interface.
 * <p>
 * Example:
 * <pre>
 * {@code @UserMessage("Hello, my name is {{UserName}}. I am {{UserAge}} years old.")
 * String chat(@K(UserName.class) String name, @K(UserAge.class) int age);}
 * </pre>
 * <p>
 * where:
 * <pre>
 * {@code public class UserName implements AgentState<String> {}"
 * public class UserAge implements AgentState<Integer> {}"}
 * </pre>
 *
 * @see UserMessage
 * @see SystemMessage
 * @see PromptTemplate
 */
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface K {

    Class<? extends AgentState<?>> value();
}
