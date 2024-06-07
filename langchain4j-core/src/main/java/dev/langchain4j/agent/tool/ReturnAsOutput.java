package dev.langchain4j.agent.tool;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Java methods annotated with @Tool and @ReturnAsOutput are considered tools that language model can use,also Directly use tool funcion return value as the output of the model
 * When using OpenAI models, <a href="https://platform.openai.com/docs/guides/function-calling">function calling</a>
 * is used under the hood.
 */

@Target(METHOD)
@Retention(RUNTIME)
public @interface ReturnAsOutput {

}
