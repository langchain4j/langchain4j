package dev.langchain4j.agent.tool;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Java methods annotated with {@code @Tool} are considered tools/functions that language model can execute/call.
 * Tool/function calling LLM capability (e.g., see <a href="https://platform.openai.com/docs/guides/function-calling">OpenAI function calling documentation</a>)
 * is used under the hood.
 * When used together with {@code AiServices}, a low-level {@link ToolSpecification} will be automatically created
 * from the method signature (e.g. method name, method parameters (names and types), {@code @Tool}
 * and @{@link P} annotations, etc.) and will be sent to the LLM.
 * If the LLM decides to call the tool, the arguments will be parsed, and the method will be called automatically.
 * If the return type of the method annotated with {@code @Tool} is {@link String}, the returned value will be sent to the LLM as-is.
 * If the return type is {@code void}, "Success" string will be sent to the LLM.
 * In all other cases, the returned value will be serialized into a JSON string and sent to the LLM.
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
     * Indicates whether the tool's String return value should be returned directly to the user.
     * Only applicable if used within an AiService and if the return type is 'Result<String>'
     * If true, the result will be returned directly without further LLM processing.
     * If false or if return type is not Result<String>, normal processing continues.
     * If true, the tool cannot be chained with other tools as the result will not be returned to the llm.
     *
     * @return whether to return the result directly
     */
    boolean returnDirectly() default false;
}
