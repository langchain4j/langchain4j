package dev.langchain4j.agent.tool;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Java methods annotated with @Tool are considered tools that language model can use.
 * When using OpenAI models, <a href="https://platform.openai.com/docs/guides/function-calling">function calling</a>
 * is used under the hood.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface Tool {

    /**
     * Name of the tool. If not provided, method name will be used.
     *
     * @return name of the tool.
     */
    String name() default "";

    /**
     * Description of the tool.
     * It should be clear and descriptive to allow language model to understand the tool's purpose and its intended use.
     *
     * @return description of the tool.
     */
    String[] value() default "";

    /**
     * if set to true,llm  use tool function return value as output,default is false and request LLM after function call to generate final answer
     * @return if use tool function return value as output.default is false
     */
    boolean returnAsFinalAnswer() default false;
}
