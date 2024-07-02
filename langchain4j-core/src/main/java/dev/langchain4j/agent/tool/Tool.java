package dev.langchain4j.agent.tool;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Java methods annotated with {@code @Tool} are considered tools/functions that language model can execute/call.
 * Tool/function calling LLM capability (e.g., see <a href="https://platform.openai.com/docs/guides/function-calling">OpenAI function calling documentation</a>)
 * is used under the hood.
 * A low-level {@link ToolSpecification} will be automatically created from the method signature
 * (e.g. method name, method parameters (names and types), @Tool and @P annotations, etc.)
 * and will be sent to the LLM.
 * If LLM decides to call the tool, the arguments are automatically parsed and injected as method arguments.
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
}
