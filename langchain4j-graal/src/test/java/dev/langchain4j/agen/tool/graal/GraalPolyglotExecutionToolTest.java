package dev.langchain4j.agen.tool.graal;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class GraalPolyglotExecutionToolTest {


    /**
     *
     * @throws NoSuchMethodException
     */
    @Test
    public void testJavascriptTool() throws NoSuchMethodException
    {
        GraalJavascriptExecutionTool jsTool =  new GraalJavascriptExecutionTool();
        String jsCode = "function fibonacci(num) {                  \n" +
                "  if (num <= 1) return 1;                          \n" +
                "                                                   \n" +
                "  return fibonacci(num - 1) + fibonacci(num - 2);  \n" +
                "}                                                  \n" +
                "fibonacci(10)";

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments( new Gson().toJson(Map.of("arg0", jsCode)))
                .build();

        ToolExecutor toolExecutor = new ToolExecutor(jsTool, GraalJavascriptExecutionTool.class.getDeclaredMethod("executeJavaScriptCode", String.class));

        String result = toolExecutor.execute(request);
        Integer fib_of_10 = new Gson().fromJson(  result, Integer.class);
        assertThat(fib_of_10).isEqualTo(89);

    }

    @Test
    public void testPythonTool() throws NoSuchMethodException
    {
        GraalPythonExecutionTool jsTool =  new GraalPythonExecutionTool();
        String jsCode = "def fibonacci_of(n):\n" +
                "    # Validate the value of n\n" +
                "    if not (isinstance(n, int) and n >= 0):\n" +
                "        raise ValueError(f'Positive integer number expected, got \"{n}\"')\n" +
                "\n" +
                "    # Handle the base cases\n" +
                "    if n in {0, 1}:\n" +
                "        return n\n" +
                "\n" +
                "    previous, fib_number = 0, 1\n" +
                "    for _ in range(2, n + 1):\n" +
                "        # Compute the next Fibonacci number, remember the previous one\n" +
                "        previous, fib_number = fib_number, previous + fib_number\n" +
                "\n" +
                "    return fib_number\n" +
                "fibonacci_of(11)";

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments( new Gson().toJson(Map.of("arg0", jsCode)))
                .build();

        ToolExecutor toolExecutor = new ToolExecutor(jsTool, GraalPythonExecutionTool.class.getDeclaredMethod("executePythonCode", String.class));

        String result = toolExecutor.execute(request);
        Integer fib_of_10 = new Gson().fromJson(  result, Integer.class);

        assertThat(fib_of_10).isEqualTo(89);

    }
}